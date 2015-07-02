package nl.knaw.dans.easy.ingest

import java.io.{FileInputStream, File}
import java.net.URI

import com.yourmediashelf.fedora.client.FedoraClient._
import com.yourmediashelf.fedora.client.request.FedoraRequest
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}

import scala.util.{Failure, Success, Try}

object Main {
  def main(args: Array[String]) {
    val credentials = new FedoraCredentials("http://deasy:8080/fedora", "fedoraAdmin", "fedoraAdmin")
    val client = new FedoraClient(credentials)
    FedoraRequest.setDefaultClient(client)

    val result = for {
      pid1 <- createDataset()
      pid2 <- createDataset()
      uri1 <- addDataStream(pid1, "id1", "text/plain")
      uri2 <- addDataStream(pid2, "id2", "text/plain")
      x <- addRelation(pid1, pid2, "belongsTo")
    } yield (pid1, pid2, uri1, uri2)

    result match {
      case Success(x) => println(x)
      case Failure(e) => e.printStackTrace()
    }

  }

  def createDataset(): Try[String] = Try {
    val pid = getNextPID.namespace("easy-dataset").execute().getPid
    ingest(pid).label(s"Label for $pid").content(new File("test-resources/test1/foxml.xml")).execute().getPid
  }

  def addDataStream(pid: String, dsId: String, mime: String): Try[URI] = Try {
    addDatastream(pid, dsId)
      .mimeType(mime)
      .controlGroup("M")
      .content(new File("test-resources/test1/quicksort.hs"))
      .execute()
      .getLocation
  }

  def addRelation(subject: String, `object`: String, predicate: String): Try[String] = Try {
    addRelationship(subject).`object`(`object`).predicate(predicate).execute().getEntity(classOf[String])
  }

}