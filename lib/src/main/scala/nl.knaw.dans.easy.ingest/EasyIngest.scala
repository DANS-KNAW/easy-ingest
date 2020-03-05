/**
 * Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.ingest

import java.io._
import java.net.{ URI, URLEncoder }

import com.yourmediashelf.fedora.client.{ FedoraClient, FedoraClientException }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.json4s._
import org.json4s.native.JsonMethods._
import resource._

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

object EasyIngest extends DebugEnhancedLogging {

  def run(implicit s: Settings): Try[PidDictionary] = {
    val result = if (s.init) initOnly _ else ingest _

    result(s.sdo)
      .doIfSuccess(dict => logger.info(s"ingested: ${ dict.values.mkString(", ") }"))
      .doIfFailure { case e => logger.error(s"ingest failed: ${ e.getMessage }", e) }
  }

  private def initOnly(sdo: File)(implicit settings: Settings): Try[PidDictionary] = {
    initSdo(sdo).map(_ => Map.empty)
  }

  private def ingest(sdo: File)(implicit settings: Settings): Try[PidDictionary] = {
    val sdos = if (isSdo(sdo)) List[File](sdo)
               else sdo.listFiles().filter(_.isDirectory).toList
    ingestStagedDigitalObjects(sdos)(new FedoraClient(settings.fedoraCredentials), settings.extraPids)
  }

  private def initSdo(dir: File)(implicit settings: Settings): Try[Unit] = {
    if (!dir.exists && !dir.mkdirs())
      Failure(new RuntimeException(s"$dir does not exist and cannot be created"))
    else if (dir.isFile) {
      Failure(new RuntimeException(s"Cannot create SDO. $dir is a file. It must either not exist or be a directory"))
    }
    else Try {
      logger.info(s"Creating $FOXML_FILENAME and $CONFIG_FILENAME from templates ...")

      FileUtils.copyFile(settings.foTemplate, new File(dir, "fo.xml"))
      FileUtils.copyFile(settings.cfgTemplate, new File(dir, "cfg.json"))
    }
  }

  private def isSdo(f: File): Boolean = f.isDirectory && f.list.contains(FOXML_FILENAME)

  private def ingestStagedDigitalObjects(sdos: List[File])(implicit fedora: FedoraClient, extraPids: PidDictionary): Try[PidDictionary] = {
    for {
      configDictionary <- buildConfigDictionary(sdos)
      pidDictionary <- ingestDigitalObjects(configDictionary, sdos)
      _ = pidDictionary.foreach(r => logger.info(s"Created digital object: $r"))
      datastreams <- addDatastreams(configDictionary, pidDictionary, sdos)
      _ = datastreams.foreach(r => debug(s"Added datastream: $r"))
      relations <- addRelations(configDictionary, pidDictionary, sdos)
      _ = relations.foreach(r => debug(s"Added relation: $r"))
    } yield pidDictionary
  }

  private def buildConfigDictionary(sdos: List[File]): Try[ConfigDictionary] = {
    logger.info(">>> PHASE 0: BUILD CONFIG DICTIONARY")
    sdos.map(d => readDOConfig(d).map(cfg => (d.getName, cfg))).collectResults.map(_.toMap)
  }

  private def ingestDigitalObjects(configDictionary: ConfigDictionary, sdos: List[File])(implicit fedora: FedoraClient, extraPids: PidDictionary): Try[PidDictionary] = {
    // the full message is repeated in the cause
    // if stripping fails due to library changes we just have a less crisp top level message
    def stripMessage(e: Throwable): String = e.getMessage.replaceAll(".*Checksum Mismatch:", "Checksum Mismatch:")

    @tailrec
    def failFastLoop(sdos: List[File], ingested: PidDictionary = Map.empty): Try[PidDictionary] = {
      sdos match {
        case Nil => Success(ingested)
        case sdo :: tail =>
          ingestDigitalObject(configDictionary, sdo) match {
            case Success(t) => failFastLoop(tail, ingested ++ List(t))
            case Failure(e) if ingested.isEmpty =>
              Failure(new Exception(s"Failed to ingest $sdo: ${ stripMessage(e) }", e))
            case Failure(e) =>
              partialFailure(ingested, s"but then failed to ingest $sdo: ${ stripMessage(e) }", e)
          }
      }
    }

    logger.info(">>> PHASE 1: INGEST DIGITAL OBJECTS")
    failFastLoop(sdos).map(_ ++ extraPids)
  }

  private def partialFailure(pidDictionary: PidDictionary, s: String, e: Throwable): Failure[Nothing] =
    Failure(new Exception(s"ingested: ${ pidDictionary.values.mkString(", ") }\n$s\n", e))

  private def addDatastreams(configDictionary: ConfigDictionary, pidDictionary: PidDictionary, sdos: List[File])(implicit fedora: FedoraClient): Try[List[URI]] = {
    logger.info(">>> PHASE 2: ADD DATASTREAMS")
    (for {
      sdo <- sdos
      _ = logger.debug(s"Adding datastreams for $sdo")
      dsSpec <- configDictionary(sdo.getName).datastreams
    } yield addDataStream(sdo, dsSpec, pidDictionary(sdo.getName))).collectResults.recoverWith {
      case e =>
        partialFailure(pidDictionary, s"but failed to add datastream(s) ${ e.getMessage }", e)
    }
  }

  private def addRelations(configDictionary: ConfigDictionary, pidDictionary: PidDictionary, sdos: List[File])(implicit fedora: FedoraClient): Try[List[(Pid, String, Pid)]] = {
    logger.info(">>> PHASE 3: ADD RELATIONS")
    logger.debug(s"configDictionary = $configDictionary")
    sdos.flatMap(sdo => {
      logger.debug(s"Adding relations for sdo $sdo")
      configDictionary(sdo.getName)
        .relations
        .map(addRelation(sdo.getName, pidDictionary))
    })
      .collectResults
      .recoverWith {
        case e => partialFailure(pidDictionary, s"but failed to add relation(s) ${ e.getMessage }", e)
      }
  }

  private def readDOConfig(sdo: File): Try[DOConfig] = {
    sdo.listFiles
      .find(_.getName == CONFIG_FILENAME)
      .map(cfgFile => Success(parse(cfgFile).extract[DOConfig]))
      .getOrElse(Failure(new RuntimeException(s"Couldn't find $CONFIG_FILENAME in ${ sdo.getName }")))
  }

  def ingestDigitalObject(configDictionary: ConfigDictionary, sdo: File)(implicit fedora: FedoraClient): Try[(ObjectName, Pid)] = {
    for {
      foxmlFile <- getFOXML(sdo)
      pid <- executeIngest(configDictionary(sdo.getName), foxmlFile)
    } yield (sdo.getName, pid)
  }

  private def executeIngest(cfg: DOConfig, foxml: File)(implicit fedora: FedoraClient): Try[Pid] = {
    managed(FedoraClient.getNextPID.namespace(cfg.namespace).execute(fedora))
      .map(_.getPid)
      .tried
      .flatMap(pid => managed(FedoraClient.ingest(pid).content(foxml).execute(fedora)).map(_.getPid).tried)
      .recoverWith {
        case e: FedoraClientException =>
          // the full fedora stack trace is in the message, it only clutters the logging
          Failure(new Exception(s"ingest failed $foxml : ${ e.getMessage.replaceAll("\n.*", "") }"))
        case e => Failure(new Exception(s"$foxml : ${ e.getMessage }"))
      }
  }

  private def addRelation(subjectName: String, pidDictionary: PidDictionary)(relation: Relation)(implicit fedora: FedoraClient): Try[(Pid, String, String)] = {
    val subjectPid: Pid = pidDictionary(subjectName)
    val objectPid = (if (relation.`object` != "") relation.`object`
                     else pidToUri(pidDictionary(relation.objectSDO)))
      .replace(PLACEHOLDER_FOR_DMO_ID, subjectPid)
    val request = FedoraClient.addRelationship(subjectPid)
      .predicate(relation.predicate)
      .`object`(objectPid, relation.isLiteral)

    managed(request.execute(fedora)).map(_ => ()).tried
      .map(_ => (subjectPid, relation.predicate, objectPid))
  }

  private def pidToUri(pid: String): String = s"info:fedora/$pid"

  private def addDataStream(sdo: File, dsSpec: DatastreamSpec, pid: Pid)(implicit fedora: FedoraClient): Try[URI] = Try {
    logger.debug(s"Getting datastreamId from spec: $dsSpec")
    val datastreamId = (dsSpec.dsID, dsSpec.contentFile) match {
      case (id, _) if id.nonEmpty => id
      case ("", file) if file.nonEmpty => file
      case _ => throw new RuntimeException(s"Invalid datastream specification provided in ${ sdo.getName }")
    }

    logger.debug(s"Adding datastream with dsId = $datastreamId")
    val requestBase = FedoraClient.addDatastream(pid, datastreamId)
      .versionable(false)
      .mimeType(dsSpec.mimeType)
      .controlGroup(dsSpec.controlGroup)

    val requestBase1 = if (dsSpec.label.nonEmpty) requestBase.dsLabel(dsSpec.label)
                       else requestBase

    val requestBase2 = if (dsSpec.checksumType.nonEmpty && dsSpec.checksum.nonEmpty)
                         requestBase1.checksumType(dsSpec.checksumType).checksum(dsSpec.checksum)
                       else requestBase1

    val request =
      if (dsSpec.dsLocation.nonEmpty)
        Success(requestBase2.dsLocation(URLEncoder.encode(dsSpec.dsLocation, "UTF-8")))
      else if (dsSpec.contentFile.isEmpty) {
        Success(requestBase2)
      }
      else
        sdo.listFiles.find(_.getName == dsSpec.contentFile)
          .map {
            case file if datastreamId == "EMD" =>
              // Note that this would change the ingested file's checksum, but it is only for the EMD datastream, which has no checksum
              managed(replacePlaceHolder(file, PLACEHOLDER_FOR_DMO_ID, pid))
                .map(requestBase2.content)
                .tried
            case file => Success(requestBase2.content(file))
          }
          .getOrElse(Failure(new RuntimeException(s"Couldn't find specified datastream: ${ dsSpec.contentFile }")))

    request.flatMap(add => managed(add.execute(fedora)).map(_.getLocation).tried)
  }.flatten

  private def replacePlaceHolder(file: File, placeholder: String, replacement: String): InputStream = {
    // these files are assumed to be small enough to be read into memory without problems
    val originalContent = FileUtils.readFileToString(file, "UTF-8")
    require(originalContent.contains(placeholder), s"Missing placeholder '$placeholder' in file: ${ file.getAbsolutePath }")
    new ByteArrayInputStream(originalContent.replace(placeholder, replacement).getBytes("UTF-8"))
  }

  private def getFOXML(sdo: File): Try[File] = {
    sdo.listFiles().find(_.getName == FOXML_FILENAME)
      .map(Success(_))
      .getOrElse(Failure(new RuntimeException(s"Couldn't find $FOXML_FILENAME in digital object: ${ sdo.getName }")))
  }
}
