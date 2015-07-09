package nl.knaw.dans.easy.ingest

import java.io._
import java.net.URI
import java.nio.file.Paths

import com.yourmediashelf.fedora.client.FedoraClient._
import com.yourmediashelf.fedora.client.request.FedoraRequest
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}

import scala.util.{Failure, Success, Try}

object Main {

  private val CONFIG_FILENAME = "do.cfg"
  private val FOXML_FILENAME = "fo.xml"

  type ObjName = String
  type Pid = String
  type Predicate = String
  type PidDictionary = Map[ObjName, Pid]

  case class Relation(predicate: Predicate, objName: ObjName)
  type Relations = Seq[Relation]

  def main(args: Array[String]) {
    val credentials = new FedoraCredentials("http://deasy:8080/fedora", "fedoraAdmin", "fedoraAdmin")
    val client = new FedoraClient(credentials)
    FedoraRequest.setDefaultClient(client)

    val stageDir = new File("test-resources/staged-test1")

    // retrieve all digital object directories
    val doDirs = stageDir.listFiles().filter(_.isDirectory)

    /*
      Digital object ingest phase (only foxml)
     */
    println(">>> PHASE 1: INGEST DIGITAL OBJECTS")

    val doIngestResults = doDirs.map(ingestDigitalObject)
    verifyIntegrity(doIngestResults)
    val pidDictionary: PidDictionary = doIngestResults.map(_.get).toMap

    pidDictionary.foreach(println)

    /*
      Add datastreams phase
     */
    println(">>> PHASE 2: ADD DATASTREAMS")

    val addDatastreamsResults = for {
      doDir <- doDirs
      file <- doDir.listFiles()
      if file.isFile && file.getName != CONFIG_FILENAME && file.getName != FOXML_FILENAME
    } yield addDataStream(file, pidDictionary(doDir.getName), file.getName, "application/octet-stream") // TODO: MIME TYPE...

    verifyIntegrity(addDatastreamsResults)

    addDatastreamsResults.foreach(println)

    /*
      Add relations phase
     */
    println(">>> PHASE 3: ADD RELATIONS")

    val addRelationsResults = doDirs.flatMap(doDir => {
      val relations = readRelations(doDir).getOrElse { throw new RuntimeException(s"Couldn't parse relations for: ${doDir.getName}") }
      relations.map(r => addRelation(pidDictionary(doDir.getName), r.predicate, pidDictionary(r.objName)))
    })

    verifyIntegrity(addRelationsResults)

    addRelationsResults.foreach(println)
  }

  private def ingestDigitalObject(doDir: File): Try[(ObjName, Pid)] =
    for {
      namespace <- readNamespace(doDir)
      foxml <- getFOXML(doDir)
      pid <- executeIngest(namespace, foxml)
    } yield (doDir.getName, pid)

  private def executeIngest(namespace: String, foxml: File): Try[Pid] = Try {
    val pid = getNextPID.namespace(namespace).execute().getPid
    ingest(pid).label(s"Label for $pid").content(foxml).execute().getPid
  }

  private def addDataStream(file: File, pid: Pid, datastreamId: Pid, mime: String): Try[URI] = Try {
    addDatastream(pid, datastreamId)
      .mimeType(mime)
      .controlGroup("M")
      .content(file)
      .execute()
      .getLocation
  }

  private def addRelation(subject: Pid, predicate: String, `object`: Pid): Try[(Pid, String, Pid)] = Try {
    addRelationship(subject).predicate(predicate).`object`(`object`).execute().close()
    (subject, predicate, `object`)
  }

  private def readNamespace(doDir: File): Try[String] = Try {
    val cfgFile = Paths.get(doDir.getPath, CONFIG_FILENAME).toFile
    val src = io.Source.fromFile(cfgFile)
    try {
      src.getLines().next()
    } finally {
      src.close()
    }
  }

  private def readRelations(doDir: File): Try[Relations] = Try {
    val cfgFile = Paths.get(doDir.getPath, CONFIG_FILENAME).toFile
    val src = io.Source.fromFile(cfgFile)
    try {
      src.getLines().toList
        .drop(1)
        .map(line => line.split(' '))
        .filter(_.length == 2)
        .map(x => Relation(predicate = x(0), objName = x(1)))
    } finally {
      src.close()
    }
  }

  private def getFOXML(doDir: File): Try[File] =
    doDir.listFiles().find(_.getName == FOXML_FILENAME) match {
      case Some(f) => Success(f)
      case None => Failure(new RuntimeException(s"Couldn't find $FOXML_FILENAME in digital object: ${doDir.getPath}"))
    }

  private def verifyIntegrity[T](results: Seq[Try[T]]) {
    if (results.exists(_.isFailure)) {
      results.collect { case Failure(e) => e }.foreach(_.printStackTrace()) // handle errors & rollback?
      System.exit(13)
    }
  }

}