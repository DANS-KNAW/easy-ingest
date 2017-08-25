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
