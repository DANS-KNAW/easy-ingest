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

import java.io.File

import com.yourmediashelf.fedora.client.FedoraCredentials
import nl.knaw.dans.lib.error._

object Command extends App {

  val configuration = Configuration()
  val clo = new CommandLineOptions(args, configuration)
  implicit val settings: Settings = Settings(
    fedoraCredentials = new FedoraCredentials(
      configuration.properties.getString("default.fcrepo-server"),
      configuration.properties.getString("default.fcrepo-username"),
      configuration.properties.getString("default.fcrepo-password")),
    sdo = new File(clo.sdo()),
    init = clo.init(),
    foTemplate = new File(configuration.properties.getString("default.fo-template")),
    cfgTemplate = new File(configuration.properties.getString("default.cfg-template"))
  )

  EasyIngest.run
    .doIfSuccess(dict => println(s"OK: Completed succesfully. Ingested: ${ dict.values.mkString(", ") }"))
    .doIfFailure { case e => println(s"FAILED: ${ e.getMessage }", e) }
}
