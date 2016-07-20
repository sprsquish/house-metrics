package smick

import com.twitter.finagle.{ Http, Service }
import com.twitter.finagle.http._
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
  val influxUser = flag("influxdb.user", "username", "Influx username")
  val influxPass = flag("influxdb.pass", "password", "Influx password")
  val influxDB = flag("influxdb.db", "database", "Influx Database")

  class InfluxStore extends Store {
    private[this] val url = new URL(
      s"http://${influxUser()}:${influxPass()}@${influxDest()}/write?db=${influxDB()}")

    @volatile private[this] var _client: Service[Request, Response] = _

    private def client = if (_client != null) _client else {
      _client = Http.newClient(influxDest()).toService
      _client
    }

    private def escape(v: Any): String = v match {
      case str: String =>
        str.replaceAll(",", "\\\\,").replaceAll(" ", "\\\\ ")
      case _ => v.toString
    }

    def write(vals: Seq[StoreEntry]): Future[Unit] =
      if (vals.isEmpty) Future.Done else {
        val body = vals map { case StoreEntry(name, value, tagMap, time) =>
          val tags = tagMap map { case (k, v) => s"""$k="${escape(v)}"""" }
          val pre = (Seq(name) ++ tags) mkString(",")
          s"""$pre value=$value ${time.getOrElse("")}"""
        } mkString("\n")

        val req = RequestBuilder().url(url).buildPost(Buf.Utf8(body))

        client(req) flatMap {
          case rep if rep.statusCode < 200 || rep.statusCode >= 300 =>
            Future.exception(WriteFail(rep.statusCode, rep.contentString))
          case _ =>
            Future.Done
        }
      }
    }
}
