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

  case class DatastreamSpec(contentFile: String = "", dsLocation: String = "", dsID: String = "", mimeType: String = "application/octet-stream", controlGroup: String = "M", checksumType: String = "", checksum: String = "")
  case class Relation(predicate: Predicate, objectSDO: ObjectName = "", `object`: Pid = "")
  case class DOConfig(namespace: String, label: String, ownerId: String, datastreams: List[DatastreamSpec], relations: List[Relation])

  class CompositeException(throwables: List[Throwable]) extends RuntimeException(throwables.foldLeft("")((msg,t) => s"$msg\n${t.getMessage}"))

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

    implicit val doDirs = stageDir.listFiles().filter(_.isDirectory).toList

    ingestStagedDigitalObjects.get
  }

  private def ingestStagedDigitalObjects(implicit doDirs: List[File]): Try[Unit] =
    for {
      configDictionary <- buildConfigDictionary
      pidDictionary <- ingestDigitalObjects(configDictionary)
      _ = pidDictionary.foreach(r => log.info(s"Created digital object: $r"))
      datastreams <- addDatastreams(configDictionary, pidDictionary)
      _ = datastreams.foreach(r => log.info(s"Added datastream: $r"))
      relations <- addRelations(configDictionary, pidDictionary)
      _ = relations.foreach(r => log.info(s"Added relation: $r"))
    } yield ()

  private def buildConfigDictionary(implicit doDirs: List[File]): Try[ConfigDictionary] = {
    log.info(">>> PHASE 0: BUILD CONFIG DICTIONARY")
    doDirs.map(d => readDOConfig(d).map(cfg => (d.getName, cfg))).sequence.map(_.toMap)
  }

  private def ingestDigitalObjects(configDictionary: ConfigDictionary)(implicit doDirs: List[File]): Try[PidDictionary] = {
    log.info(">>> PHASE 1: INGEST DIGITAL OBJECTS")
    doDirs.map(ingestDigitalObject(configDictionary)).sequence.map(_.toMap)
  }

  private def addDatastreams(configDictionary: ConfigDictionary, pidDictionary: PidDictionary)(implicit doDirs: List[File]): Try[List[URI]] = {
    log.info(">>> PHASE 2: ADD DATASTREAMS")
    val results = for {
      doDir <- doDirs
      file <- doDir.listFiles()
      if file.isFile && file.getName != CONFIG_FILENAME && file.getName != FOXML_FILENAME
      dsSpec = configDictionary(doDir.getName).datastreams.find(_.contentFile == file.getName)
        .getOrElse(throw new RuntimeException(s"Can't find specification for datastream: ${doDir.getName}/${file.getName}"))
    } yield addDataStream(file, pidDictionary(doDir.getName), dsSpec)
    results.sequence
  }

  private def addRelations(configDictionary: ConfigDictionary, pidDictionary: PidDictionary)(implicit doDirs: List[File]): Try[List[(Pid, String, Pid)]] = {
    log.info(">>> PHASE 3: ADD RELATIONS")
    doDirs.flatMap(doDir => {
      val relations = configDictionary(doDir.getName).relations
      relations.map(addRelation(doDir.getName, pidDictionary))
    }).sequence
  }

  private def readDOConfig(doDir: File): Try[DOConfig] =
    doDir.listFiles.find(_.getName == CONFIG_FILENAME) match {
      case Some(cfgFile) => Success(parse(cfgFile).extract[DOConfig])
      case None => Failure(new RuntimeException(s"Couldn't find $CONFIG_FILENAME in ${doDir.getName}"))
    }

  private def ingestDigitalObject(configDictionary: ConfigDictionary)(doDir: File): Try[(ObjectName, Pid)] =
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

  private def addRelation(subjectName: String, pidDictionary: PidDictionary)(relation: Relation): Try[(Pid, String, Pid)] = Try {
    val subjectPid: Pid = pidDictionary(subjectName)
    val objectPid = if (relation.`object` != "") relation.`object` else pidDictionary(relation.objectSDO)
    addRelationship(subjectPid).predicate(relation.predicate).`object`(objectPid).execute().close()
    (subjectPid, relation.predicate, objectPid)
  }

  private def getFOXML(doDir: File): Try[File] =
    doDir.listFiles().find(_.getName == FOXML_FILENAME) match {
      case Some(f) => Success(f)
      case None => Failure(new RuntimeException(s"Couldn't find $FOXML_FILENAME in digital object: ${doDir.getName}"))
    }

  private def verifyIntegrity[T](results: Seq[Try[T]]): Unit =
    if (results.exists(_.isFailure)) {
      results.collect { case Failure(e) => e }.foreach(e => log.error(e.getMessage, e)) // handle errors & rollback?
      System.exit(13)
    }

  implicit class ListTryExtensions[T](xs: List[Try[T]]) {
    def sequence: Try[List[T]] =
      if (xs.exists(_.isFailure))
        Failure(new CompositeException(xs.collect{ case Failure(e) => e }))
      else
        Success(xs.map(_.get))
  }

}