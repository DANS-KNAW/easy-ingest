/**
 * Copyright (C) 2015-2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.ingest

import java.io._
import java.net.URI

import com.yourmediashelf.fedora.client.FedoraClient
import com.yourmediashelf.fedora.client.FedoraClient._
import com.yourmediashelf.fedora.client.request.{AddDatastream, FedoraRequest}
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.exception.ExceptionUtils._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object EasyIngest {
  private val log = LoggerFactory.getLogger(getClass)
  private val home = new File(System.getProperty("app.home"))
  val props = new PropertiesConfiguration(new File(home, "cfg/application.properties"))
  private implicit val formats = DefaultFormats

  private val CONFIG_FILENAME = "cfg.json"
  private val FOXML_FILENAME = "fo.xml"

  private type ObjectName = String
  private type Pid = String
  private type Predicate = String
  private type ConfigDictionary = Map[ObjectName, DOConfig]
  type PidDictionary = Map[ObjectName, Pid]

  private case class DatastreamSpec(contentFile: String = "", dsLocation: String = "", dsID: String = "", mimeType: String = "application/octet-stream", controlGroup: String = "M", checksumType: String = "", checksum: String = "")
  private case class Relation(predicate: Predicate, objectSDO: ObjectName = "", `object`: Pid = "", isLiteral: Boolean = false)
  private case class DOConfig(namespace: String, datastreams: List[DatastreamSpec], relations: List[Relation])

  private class CompositeException(throwables: List[Throwable]) extends RuntimeException(throwables.foldLeft("")((msg, t) => s"$msg\n${getMessage(t)} ${getStackTrace(t)}"))

  def main(args: Array[String]) {
    implicit val s: Settings = Settings(new Conf(args, props))
    run.get
  }

  def run(implicit s: Settings): Try[PidDictionary] = {
    val sdo = s.sdo
    if(s.init) initSdo(sdo).map(_ => Map())
    else {
      FedoraRequest.setDefaultClient(new FedoraClient(s.fedoraCredentials))
      implicit val sdos = if (isSdo(sdo)) List[File](sdo)
                          else sdo.listFiles().filter(_.isDirectory).toList
      ingestStagedDigitalObjects
    }
  }

  private def initSdo(dir: File): Try[Unit] = Try {
    if(!dir.exists && !dir.mkdirs()) throw new RuntimeException(s"$dir does not exist and cannot be created")
    if(dir.isFile) throw new RuntimeException(s"Cannot create SDO. $dir is a file. It must either not exist or be a directory")
    log.info(s"Creating $FOXML_FILENAME and $CONFIG_FILENAME from templates ...")
    FileUtils.copyFile(new File(home, "cfg/fo-template.xml"), new File(dir, "fo.xml"))
    FileUtils.copyFile(new File(home, "cfg/cfg-template.json"), new File(dir, "cfg.json"))
  }

  private def isSdo(f: File): Boolean = f.isDirectory && f.list.contains(FOXML_FILENAME)

  private def ingestStagedDigitalObjects(implicit sdos: List[File]): Try[PidDictionary] =
    for {
      configDictionary <- buildConfigDictionary
      pidDictionary <- ingestDigitalObjects(configDictionary)
      _ = pidDictionary.foreach(r => log.info(s"Created digital object: $r"))
      datastreams <- addDatastreams(configDictionary, pidDictionary)
      _ = datastreams.foreach(r => log.debug(s"Added datastream: $r"))
      relations <- addRelations(configDictionary, pidDictionary)
      _ = relations.foreach(r => log.debug(s"Added relation: $r"))
    } yield pidDictionary

  private def buildConfigDictionary(implicit sdos: List[File]): Try[ConfigDictionary] = {
    log.info(">>> PHASE 0: BUILD CONFIG DICTIONARY")
    sdos.map(d => readDOConfig(d).map(cfg => (d.getName, cfg))).sequence.map(_.toMap)
  }

  private def ingestDigitalObjects(configDictionary: ConfigDictionary)(implicit sdos: List[File]): Try[PidDictionary] = {
    log.info(">>> PHASE 1: INGEST DIGITAL OBJECTS")
    sdos.map(ingestDigitalObject(configDictionary)).sequence.map(_.toMap)
  }

  private def addDatastreams(configDictionary: ConfigDictionary, pidDictionary: PidDictionary)(implicit sdos: List[File]): Try[List[URI]] = {
    log.info(">>> PHASE 2: ADD DATASTREAMS")
    (for {
      sdo <- sdos
      _ = log.debug(s"Adding datastreams for $sdo")
      dsSpec <- configDictionary(sdo.getName).datastreams
    } yield addDataStream(sdo, dsSpec, pidDictionary)).sequence
  }

  private def addRelations(configDictionary: ConfigDictionary, pidDictionary: PidDictionary)(implicit sdos: List[File]): Try[List[(Pid, String, Pid)]] = {
    log.info(">>> PHASE 3: ADD RELATIONS")
    log.debug(s"configDictionary = $configDictionary")
    sdos.flatMap(sdo => {
      log.debug(s"Adding relations for sdo $sdo")
      val relations = configDictionary(sdo.getName).relations
      relations.map(addRelation(sdo.getName, pidDictionary))
    }).sequence
  }

  private def readDOConfig(sdo: File): Try[DOConfig] =
    sdo.listFiles.find(_.getName == CONFIG_FILENAME) match {
      case Some(cfgFile) => Success(parse(cfgFile).extract[DOConfig])
      case None => Failure(new RuntimeException(s"Couldn't find $CONFIG_FILENAME in ${sdo.getName}"))
    }

  private def ingestDigitalObject(configDictionary: ConfigDictionary)(sdo: File): Try[(ObjectName, Pid)] =
    for {
      foxml <- getFOXML(sdo)
      pid <- executeIngest(configDictionary(sdo.getName), foxml)
    } yield (sdo.getName, pid)

  private def executeIngest(cfg: DOConfig, foxml: File): Try[Pid] = Try {
    val pid = getNextPID.namespace(cfg.namespace).execute().getPid
    ingest(pid)
      .content(foxml)
      .execute()
      .getPid
  }

  private def addRelation(subjectName: String, pidDictionary: PidDictionary)(relation: Relation): Try[(Pid, String, Pid)] = Try {
    val subjectPid: Pid = pidDictionary(subjectName)
    val objectPid = if (relation.`object` != "") relation.`object` else pidToUri(pidDictionary(relation.objectSDO))
    addRelationship(subjectPid).predicate(relation.predicate).`object`(objectPid, relation.isLiteral).execute().close()
    (subjectPid, relation.predicate, objectPid)
  }

  private def pidToUri(pid: String): String = s"info:fedora/$pid"

  private def addDataStream(sdo: File, dsSpec: DatastreamSpec, pidDictionary: PidDictionary): Try[URI] = Try {
    log.debug(s"Getting datastreamId from spec: $dsSpec")
    val datastreamId = (dsSpec.dsID, dsSpec.contentFile, dsSpec.dsLocation) match {
      case (id, _, _) if id != "" => id
      case ("", file, _) if file != "" => file
      case _ => throw new RuntimeException(s"Invalid datastream specification provided in ${sdo.getName}")
    }

    log.debug(s"Adding datastream with dsId = $datastreamId")
    var request = addDatastream(pidDictionary(sdo.getName), datastreamId).mimeType(dsSpec.mimeType).controlGroup(dsSpec.controlGroup)

    if (dsSpec.checksumType != "" && dsSpec.checksum != "")
      request = request.checksumType(dsSpec.checksumType).checksum(dsSpec.checksum)

    if (dsSpec.dsLocation != "") {
      request = request.dsLocation(dsSpec.dsLocation)
    } else if (dsSpec.contentFile != "") {
      request = (datastreamId, sdo.listFiles.find(_.getName == dsSpec.contentFile)) match {
        case ("EMD", Some(file)) =>
          // Note that this would change the ingested file's checksum, but it is only for the EMD datastream, which has no checksum
          return Success(executeAddRequestWithReplacement(request, file, "$sdo-id", pidDictionary(sdo.getName)))
        case (_, Some(file)) => request.content(file)
        case (_, None) => throw new RuntimeException(s"Couldn't find specified datastream: ${dsSpec.contentFile}")
      }
    }
    request.execute().getLocation
  }

  private def executeAddRequestWithReplacement(request: AddDatastream, file: File, placeholder: String, replacement:String): URI = {
    val tmpFile = File.createTempFile(file.getName, null)
    log.debug(s"Created temp file: '$tmpFile.getAbsolutePath'")
    replacePlaceholderInFileCopy(file, tmpFile, placeholder, replacement)
    request.content(tmpFile)
    val location = request.execute().getLocation
    log.debug(s"Deleting temp file: '$tmpFile.getAbsolutePath'")
    FileUtils.deleteQuietly(tmpFile)
    location
  }

  private def replacePlaceholderInFileCopy(src:File, dst:File, placeholder:String, replacement:String): Unit = {
    // these files are assumed to be small enough to be read into memory without problems
    val srcContent = FileUtils.readFileToString(src)
    if (!srcContent.contains(placeholder))
      throw new RuntimeException(s"Missing placeholder '$placeholder' in file: ${src.getAbsolutePath}")
    val transformedContent = srcContent.replaceAll(placeholder, replacement)
    FileUtils.writeStringToFile(dst, transformedContent)
    log.debug(s"Replaced placeholder '$placeholder' with '$replacement' while copying from file '${src.getAbsolutePath}', to file '${dst.getAbsolutePath}'")
  }

  private def getFOXML(sdo: File): Try[File] =
    sdo.listFiles().find(_.getName == FOXML_FILENAME) match {
      case Some(f) => Success(f)
      case None => Failure(new RuntimeException(s"Couldn't find $FOXML_FILENAME in digital object: ${sdo.getName}"))
    }

  private def verifyIntegrity[T](results: Seq[Try[T]]): Unit =
    if (results.exists(_.isFailure)) {
      results.collect { case Failure(e) => e }.foreach(e => log.error(e.getMessage, e)) // handle errors & rollback?
      System.exit(13)
    }

  private implicit class ListTryExtensions[T](xs: List[Try[T]]) {
    def sequence: Try[List[T]] =
      if (xs.exists(_.isFailure))
        Failure(new CompositeException(xs.collect { case Failure(e) => e }))
      else
        Success(xs.map(_.get))
  }

}
