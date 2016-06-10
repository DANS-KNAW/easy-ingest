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

import java.net.URL

import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop.ScallopConf

class Conf(args: Seq[String], props: PropertiesConfiguration) extends ScallopConf(args) {
  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-ingest"
  val indent_____ = printedName.replaceAll(".", " ")
  version(s"$printedName v${Version()}")
  banner(s"""
                |Ingest Staged Digital Objects (SDO's) into a Fedora Commons 3.x repository.
                |
                |Usage:
                |
                | $printedName [-u <user> -p <password>] [-f <fcrepo-server>] [-i] \\
                | $indent_____ [<staged-digital-object>... | <staged-digital-object-set>]
                |
                |Options:
                |""".stripMargin)
  private val s = " provided, please check {{app.home}}/cfg/application.properties"
  val fedoraUrl = opt[URL](name = "fcrepo-server",
    descr = "URL of the Fedora Commons Repository Server",
    default = props.getString("default.fcrepo-server") match {
      case s: String => Some(new URL(s))
      case _ =>
        throw new RuntimeException("No valid default Fedora Commons URL" + s)
    })
  val username = opt[String]("username",
    descr = "Username to use for authentication/authorisation to the Fedora Commons Repository Server",
    default = props.getString("default.user") match {
      case s: String => Some(s)
      case _ => throw new RuntimeException("No default username" + s)
    })
  val password = opt[String]("password",
    descr = "Password to use for authentication/authorisation to the Fedora Commons Repository Server",
    default = props.getString("default.password") match {
      case s: String => Some(s)
      case _ => throw new RuntimeException("No default password" + s)
    })
  val init = opt[Boolean](name = "init",
    descr = "Initialize template SDO instead of ingesting",
    default = Some(false),
    required = false)
  val sdo = trailArg[String](
    name = "<staged-digital-object-(set)>",
    descr = "Either a single Staged Digital Object or a set of SDO's",
    required = true)
  verify()
} 