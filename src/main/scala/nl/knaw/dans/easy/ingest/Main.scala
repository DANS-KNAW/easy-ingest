/*******************************************************************************
  * Copyright 2015 DANS - Data Archiving and Networked Services
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  ******************************************************************************/

package nl.knaw.dans.easy.ingest

import java.io._
import java.net.URI

import com.yourmediashelf.fedora.client.FedoraClient._
import com.yourmediashelf.fedora.client.request.FedoraRequest
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object Main {
  val log = LoggerFactory.getLogger(getClass)
  implicit val formats = DefaultFormats

  private val CONFIG_FILENAME = "cfg.json"
  private val FOXML_FILENAME = "fo.xml"

  type ObjectName = String
  type Pid = String
  type Predicate = String
  type PidDictionary = Map[ObjectName, Pid]
  type ConfigDictionary = Map[ObjectName, DOConfig]

  case class FileSpec(filename: String, mime: String)
  case class Relation(predicate: Predicate, objectName: ObjectName)
  case class DOConfig(namespace: String, files: List[FileSpec], relations: List[Relation])

  def main(args: Array[String]) {

    val credentials = new FedoraCredentials(Properties("fedora-host"), Properties("fedora-user"), Properties("fedora-password"))
    val client = new FedoraClient(credentials)
    FedoraRequest.setDefaultClient(client)

    val stageDir = new File("test-resources/staged-test1")

    // retrieve all digital object directories
    val doDirs = stageDir.listFiles().filter(_.isDirectory)

    /*
      Phase 0: Build config dictionary
     */
    log.info(">>> PHASE 0: BUILD CONFIG DICTIONARY")

    val buildConfigDictionaryResults = doDirs.map(d => readDOConfig(d).map(cfg => (d.getName, cfg)))
    verifyIntegrity(buildConfigDictionaryResults)
    implicit val configDictionary: ConfigDictionary = buildConfigDictionaryResults.map(_.get).toMap

    /*
      Phase 1: Ingest digital objects (only foxml)
     */
    log.info(">>> PHASE 1: INGEST DIGITAL OBJECTS")

    val doIngestResults = doDirs.map(ingestDigitalObject)
    verifyIntegrity(doIngestResults)
    val pidDictionary: PidDictionary = doIngestResults.map(_.get).toMap

    pidDictionary.foreach(r => log.info(s"Created digital object: $r"))

    /*
      Phase 2: Add datastreams
     */
    log.info(">>> PHASE 2: ADD DATASTREAMS")

    val addDatastreamsResults = for {
      doDir <- doDirs
      file <- doDir.listFiles()
      if file.isFile && file.getName != CONFIG_FILENAME && file.getName != FOXML_FILENAME
      mime = configDictionary(doDir.getName).files.find(_.filename == file.getName).map(_.mime).getOrElse("application/octet-stream")
    } yield addDataStream(file, pidDictionary(doDir.getName), file.getName, mime)

    verifyIntegrity(addDatastreamsResults)

    addDatastreamsResults.map(_.get).foreach(r => log.info(s"Added datastream: $r"))

    /*
      Phase 3: Add relations
     */
    log.info(">>> PHASE 3: ADD RELATIONS")

    val addRelationsResults = doDirs.flatMap(doDir => {
      val relations = configDictionary(doDir.getName).relations
      relations.map(r => addRelation(pidDictionary(doDir.getName), r.predicate, pidDictionary(r.objectName)))
    })

    verifyIntegrity(addRelationsResults)

    addRelationsResults.map(_.get).foreach(r => log.info(s"Added relation: $r"))
  }


  private def readDOConfig(doDir: File): Try[DOConfig] =
    doDir.listFiles.find(_.getName == CONFIG_FILENAME) match {
      case Some(cfgFile) => Success(parse(cfgFile).extract[DOConfig])
      case None => Failure(new RuntimeException(s"Couldn't find $CONFIG_FILENAME in ${doDir.getName}"))
    }

  private def ingestDigitalObject(doDir: File)(implicit configDictionary: ConfigDictionary): Try[(ObjectName, Pid)] =
    for {
      foxml <- getFOXML(doDir)
      pid <- executeIngest(configDictionary(doDir.getName).namespace, foxml)
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

  private def getFOXML(doDir: File): Try[File] =
    doDir.listFiles().find(_.getName == FOXML_FILENAME) match {
      case Some(f) => Success(f)
      case None => Failure(new RuntimeException(s"Couldn't find $FOXML_FILENAME in digital object: ${doDir.getPath}"))
    }

  private def verifyIntegrity[T](results: Seq[Try[T]]): Unit =
    if (results.exists(_.isFailure)) {
      results.collect { case Failure(e) => e }.foreach(e => log.error(e.getMessage, e)) // handle errors & rollback?
      System.exit(13)
    }

}