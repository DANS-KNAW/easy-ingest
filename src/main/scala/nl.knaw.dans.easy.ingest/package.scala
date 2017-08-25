package nl.knaw.dans.easy

import org.json4s.DefaultFormats

package object ingest {

  implicit val formats: DefaultFormats.type = DefaultFormats

  val CONFIG_FILENAME = "cfg.json"
  val FOXML_FILENAME = "fo.xml"
  val PLACEHOLDER_FOR_DMO_ID = "$sdo-id"

  type ObjectName = String
  type Pid = String
  type Predicate = String
  type ConfigDictionary = Map[ObjectName, DOConfig]
  type PidDictionary = Map[ObjectName, Pid]

  case class DatastreamSpec(contentFile: String = "",
                            dsLocation: String = "",
                            dsID: String = "",
                            mimeType: String = "application/octet-stream",
                            controlGroup: String = "M",
                            checksumType: String = "",
                            checksum: String = "")
  case class Relation(predicate: Predicate,
                      objectSDO: ObjectName = "",
                      `object`: Pid = "",
                      isLiteral: Boolean = false)
  case class DOConfig(namespace: String,
                      datastreams: List[DatastreamSpec],
                      relations: List[Relation])
}
