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

import org.rogach.scallop.{ ScallopConf, ScallopOption }

class CommandLineOptions(args: Seq[String], configuration: Configuration) extends ScallopConf(args) {

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-ingest"
  version(s"$printedName ${ configuration.version }")
  val description = "Ingest Staged Digital Objects (SDO's) into a Fedora Commons 3.x repository."
  val synopsis = s"$printedName [-i] [<staged-digital-object> | <staged-digital-object-set>]"
  banner(
    s"""
       |$description
       |
       |Usage:
       |
       |$synopsis
       |
       |Options:
       |""".stripMargin
  )

  val init: ScallopOption[Boolean] = opt[Boolean](name = "init",
    descr = "Initialize template SDO instead of ingesting",
    default = Some(false),
    required = false)
  val sdo: ScallopOption[String] = trailArg[String](
    name = "<staged-digital-object-(set)>",
    descr = "Either a single Staged Digital Object or a set of SDO's",
    required = true)

  verify()
} 
