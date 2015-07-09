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
  type Relations = List[(Predicate, Pid)]

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
    println(">>>>>> PHASE 1: INGEST DIGITAL OBJECTS")

    val doIngestResults = doDirs.map(ingestDigitalObject)
    verifyIntegrity(doIngestResults)
    val pidDictionary: PidDictionary = doIngestResults.map(_.get).toMap

    pidDictionary.foreach(println)

    /*
      Add datastreams phase
     */
    println(">>>>>> PHASE 2: ADD DATASTREAMS")

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
    println(">>>>>> PHASE 3: ADD RELATIONS")

//    val result = for {
//      pid1 <- ingestDO()
//      pid2 <- ingestDO()
//      uri1 <- addDataStream(pid1, "id1", "text/plain")
//      uri2 <- addDataStream(pid2, "id2", "text/plain")
//      _ <- addRelation(pid1, "fedora:isMemberOf", pid2)      // object should be parent digital object
//      _ <- addRelation(pid1, "fedora:isSubordinateTo", pid2) // object should be dataset
//    } yield (pid1, pid2, uri1, uri2)
//
//    result match {
//      case Success(x) => println(x)
//      case Failure(e) => e.printStackTrace()
//    }
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

  private def addRelation(subject: Pid, predicate: String, `object`: Pid): Try[String] = Try {
    addRelationship(subject).predicate(predicate).`object`(`object`).execute().getEntity(classOf[String])
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