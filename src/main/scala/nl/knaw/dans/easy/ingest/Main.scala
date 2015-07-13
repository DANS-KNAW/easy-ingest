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

  case class DatastreamSpec(contentFile: String, dsID: String = "", mimeType: String = "application/octet-stream", controlGroup: String = "M", checksumType: String = "", checksum: String = "")
  case class Relation(predicate: Predicate, objectSDO: ObjectName = "", `object`: Pid = "")
  case class DOConfig(namespace: String, label: String, ownerId: String, datastreams: List[DatastreamSpec], relations: List[Relation])

  def main(args: Array[String]) {
    // TODO: use Scallop for solid command line parsing
    if(args.length < 1) {
      println("Not enough arguments")
      System.exit(1)
    }
    val stageDir = new File(args(0))
    val credentials = new FedoraCredentials(Properties("default.fcrepo-server"), Properties("default.user"), Properties("default.password"))
    val client = new FedoraClient(credentials)
    FedoraRequest.setDefaultClient(client)
    val doDirs = stageDir.listFiles().filter(_.isDirectory)
 
    log.info(">>> PHASE 0: BUILD CONFIG DICTIONARY")
    val buildConfigDictionaryResults = doDirs.map(d => readDOConfig(d).map(cfg => (d.getName, cfg)))
    verifyIntegrity(buildConfigDictionaryResults)
    implicit val configDictionary: ConfigDictionary = buildConfigDictionaryResults.map(_.get).toMap

    log.info(">>> PHASE 1: INGEST DIGITAL OBJECTS")
    val doIngestResults = doDirs.map(ingestDigitalObject)
    verifyIntegrity(doIngestResults)
    implicit val pidDictionary: PidDictionary = doIngestResults.map(_.get).toMap
    pidDictionary.foreach(r => log.info(s"Created digital object: $r"))

    log.info(">>> PHASE 2: ADD DATASTREAMS")
    val addDatastreamsResults = for {
      doDir <- doDirs
      file <- doDir.listFiles()
      if file.isFile && file.getName != CONFIG_FILENAME && file.getName != FOXML_FILENAME
      dsSpec = configDictionary(doDir.getName).datastreams.find(_.contentFile == file.getName)
        .getOrElse(throw new RuntimeException(s"Can't find specification for datastream: ${doDir.getName}/${file.getName}"))
    } yield addDataStream(file, pidDictionary(doDir.getName), dsSpec)
    verifyIntegrity(addDatastreamsResults)
    addDatastreamsResults.map(_.get).foreach(r => log.info(s"Added datastream: $r"))

    log.info(">>> PHASE 3: ADD RELATIONS")
    val addRelationsResults = doDirs.flatMap(doDir => {
      val relations = configDictionary(doDir.getName).relations
      relations.map(addRelation(pidDictionary(doDir.getName), _))
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
      pid <- executeIngest(configDictionary(doDir.getName), foxml)
    } yield (doDir.getName, pid)

  private def executeIngest(cfg: DOConfig, foxml: File): Try[Pid] = Try {
    val pid = getNextPID.namespace(cfg.namespace).execute().getPid
    ingest(pid)
      .label(cfg.label)
      .ownerId(cfg.ownerId)
      .content(foxml)
      .execute()
      .getPid
  }

  private def addDataStream(file: File, doPid: Pid, dsSpec: DatastreamSpec): Try[URI] = Try {
    val datastreamId = if (dsSpec.dsID != "") dsSpec.dsID else dsSpec.contentFile
    val request =
      if (dsSpec.checksumType == "")
        addDatastream(doPid, datastreamId)
          .content(file)
          .mimeType(dsSpec.mimeType)
          .controlGroup(dsSpec.controlGroup)
      else
        addDatastream(doPid, datastreamId)
          .content(file)
          .mimeType(dsSpec.mimeType)
          .controlGroup(dsSpec.controlGroup)
          .checksumType(dsSpec.checksumType)
          .checksum(dsSpec.checksum)
    request.execute().getLocation
  }

  private def addRelation(subjectPid: Pid, relation: Relation)(implicit pidDictionary: PidDictionary): Try[(Pid, String, Pid)] = Try {
    val objectPid = if (relation.`object` != "") relation.`object` else pidDictionary(relation.objectSDO)
    addRelationship(subjectPid).predicate(relation.predicate).`object`(objectPid).execute().close()
    (subjectPid, relation.predicate, objectPid)
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