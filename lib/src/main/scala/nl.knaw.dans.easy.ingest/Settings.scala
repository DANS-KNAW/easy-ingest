/*
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

case class Settings(fedoraCredentials: FedoraCredentials,
                    sdo: File,
                    init: Boolean = false,
                    foTemplate: File,
                    cfgTemplate: File,
                    extraPids: PidDictionary = Map.empty,
                   ) {

  override def toString: String = {
    s"ingest.Settings(Fedora(${ fedoraCredentials.getBaseUrl }, ${ fedoraCredentials.getUsername }, " +
      s"****), sdo = $sdo, init = $init, fo-template = $foTemplate, cfg-template = $cfgTemplate, extraPids = $extraPids)"
  }
}
