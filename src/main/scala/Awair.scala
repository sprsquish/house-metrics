package smick

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.{ Fields, Request, RequestBuilder }
import com.twitter.util.{ TimeFormat, Future }
import java.net.URL
import scala.util.control.NonFatal

private case class AwairDevice(name: String, typ: String, id: String)
private object AwairDevice {
  def unapply(str: String): Option[AwairDevice] = {
    try {
      val Array(n, t, i) = str.split(':')
      Some(AwairDevice(n, t, i))
    } catch {
      case NonFatal(_) => None
    }
  }
}

trait Awair { self: SmickHome =>
  val awairFreq = flag("awair.freq", 5.minutes, "Loop frequency for Awair API calls")
  val awairToken = flag("awair.token", "", "Access token for Awair")
  val awairDevices = flag("awair.devices", Seq.empty[String], "List of devices: 'name:type:id'")

  private[this] val timeFormat = new TimeFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def awairLoop(store: Store): Future[Unit] =
    loopIt("awair", awairFreq(), process(store))

  private def devURL(typ: String, id: String): URL =
    new URL(s"https://developer-apis.awair.is/v1/users/self/devices/${typ}/${id}/air-data/latest")

  private[this] val devices = Lazy[Seq[AwairDevice]] {
    awairDevices() collect { case AwairDevice(dev) => dev }
  }

  private[this] val clients = Lazy[Seq[(String, URL, HttpSvc, Request)]] {
    devices() map { case AwairDevice(n, t, i) =>
      val _url = devURL(t, i)
      val c = Http.client
        .withLabel(s"awair:$n")
        .withTls(_url.getHost)
        .newClient(destStr(_url))
        .toService
      val req = RequestBuilder()
        .setHeader(Fields.Authorization, s"Bearer ${awairToken()}")
        .url(_url)
        .buildGet()
      (n, _url, c, req)
    }
  }

  private def process(store: Store): Future[Unit] = Future.join {
    clients() map { case (device, url, client, req) =>
      client(req) flatMap { res =>
        val recs = json.readValue[Map[String, Any]](res.contentString)
        val data = recs("data").asInstanceOf[List[Map[String, Any]]]
        val entries = data flatMap { data =>
          val ts = {
            val raw = data("timestamp").asInstanceOf[String]
            val d = timeFormat.parse(raw)
            Some(d.inNanoseconds)
          }

          data("sensors").asInstanceOf[Seq[Map[String, Any]]] map { sensor =>
            val name = sensor("comp").asInstanceOf[String]
            StoreEntry(
              s"awair.$name", sensor("value"),
              tags = Map("device" -> device),
              time = ts)
          }
        }

        store.write(entries)
      }
    }
  }
}
