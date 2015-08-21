package nl.knaw.dans.easy.ingest

import java.io.File

import com.yourmediashelf.fedora.client.FedoraCredentials

object Settings{
  def apply(conf: Conf): Settings =
    new Settings(
      new FedoraCredentials(conf.fedoraUrl(), conf.username(), conf.password()),
      conf.sdo(),
      conf.init())
}

case class Settings(fedoraCredentials: FedoraCredentials,
                    sdo: File,
                    init: Boolean = false)
