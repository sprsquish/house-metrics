package smick

import com.twitter.finagle.Http
import com.twitter.finagle.http.{ Fields, RequestBuilder }
import com.twitter.io.Buf
import com.twitter.util.Future
import java.net.URL

case class WriteFail(code: Int, body: String) extends Exception(s"$code: $body")

trait Store {
  def write(vals: Seq[StoreEntry]): Future[Unit]
}

case class StoreEntry(
  name: String,
  value: Any,
  tags: Map[String,Any] = Map.empty,
  time: Option[Long] = None)

trait InfluxDB { self: SmickHome =>
  val influxDest = flag("influxdb.dest", "hostname:8086", "Dest of influxDB")
  val influxBucket = flag("influxdb.bucket", "bucket", "Influx Database")
  val influxToken = flag("influxdb.token", "my-token", "Influx auth token")
  val influxOrg = flag("influxdb.org", "my-org", "Influx Org")

  class InfluxStore extends Store {
    private[this] val url = new URL(
      s"http://${influxDest()}/api/v2/write?org=${influxOrg()}&bucket=${influxBucket()}")

    private[this] val client = Lazy[HttpSvc] {
      Http.client
        .withLabel("influxdb")
        .newClient(influxDest())
        .toService
    }

    private def escape(v: Any): String = v match {
      case str: String =>
        str.replaceAll(",", "\\\\,").replaceAll(" ", "\\\\ ")
      case _ => v.toString
    }

    def write(vals: Seq[StoreEntry]): Future[Unit] =
      if (vals.isEmpty) Future.Done else {
        log.debug(s"Writing: $vals")
        val body = vals map { case StoreEntry(name, value, tagMap, time) =>
          val tags = tagMap map { case (k, v) => s"""$k="${escape(v)}"""" }
          val pre = (Seq(name) ++ tags) mkString(",")
          s"""$pre value=$value ${time.getOrElse("")}"""
        } mkString("\n")

        val req = RequestBuilder().url(url)
          .setHeader(Fields.Authorization, s"Token ${influxToken()}")
          .setHeader(Fields.ContentType, "text/plain; charset=utf-8")
          .setHeader(Fields.Accept, "application/json")
          .buildPost(Buf.Utf8(body))

        client()(req) flatMap {
          case rep if rep.statusCode < 200 || rep.statusCode >= 300 =>
            Future.exception(WriteFail(rep.statusCode, rep.contentString))
          case _ =>
            Future.Done
        }
      }
    }
}
