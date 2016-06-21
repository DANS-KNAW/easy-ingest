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
import java.io.{ByteArrayOutputStream, File}

import nl.knaw.dans.easy.ingest.CustomMatchers._
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.io.FileUtils
import org.scalatest.{FlatSpec, Matchers}

class ConfSpec extends FlatSpec with Matchers {


  val helpInfo = {
    val propsFile = new File("target/test/application.properties")
    FileUtils.write(propsFile, "default.fcrepo-server=http://deasy.dans.knaw.nl:8080/fedora\n" +
      "default.user=u\n" +
      "default.password=p")
    val props = new PropertiesConfiguration(propsFile)
    propsFile.delete()

    val mockedStdOut = new ByteArrayOutputStream()
    Console.withOut(mockedStdOut) {
      new Conf("args".split(" "), props).printHelp()
    }
    mockedStdOut.toString
  }

  /* check for options in help info is spoilt by links in readme
   * and defaults from {{app.home}}/cfg/application.properties
   * requiring a semantically valid fedora URL
   */
  ignore should "be part of README.md" in {
    val options = helpInfo.replaceAll("\\(default[^)]+\\)","").split("Options:")(1)
    new File("README.md") should containTrimmed(options)
  }

  "synopsis in help info" should "be part of README.md" in {
    val synopsis = helpInfo.split("Options:")(0).split("Usage:")(1)
    new File("README.md") should containTrimmed(synopsis)
  }

  "first banner line" should "be part of README.md and pom.xml" in {
    val description = helpInfo.split("\n")(1)
    new File("README.md") should containTrimmed(description)
    new File("pom.xml") should containTrimmed(description)
  }
}
