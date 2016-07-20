package smick

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.finagle.http._
import com.twitter.finagle.{ Http, Service }
import com.twitter.io.Buf
import com.twitter.util.Future
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import scala.compat.java8.FunctionConverters._

object Nest {
  type Data = Map[String, Map[String, Map[String, Any]]]
}

case class NestResult(path: String, data: Nest.Data)

trait Nest { self: SmickHome =>

  val nestUrl = flag("nest.url", "https://developer-api.nest.com/devices.json", "URL for nest")
  val nestAuth = flag("nest.auth", "authstring", "Nest auth code")

  def nestLoop(store: Store): Future[Unit] =
    loopIt("nest", 1.minute, process(store))

  private def translate(v: Any): Any = v match {
    // alarm state (ok is also battery health)
    case "ok" => 0
    case "warning" => 1
    case "emergency" => 2

    // battery health
    case "replace" => 1

    // hvac state (off is also hvac_mode)
    case "off" => 0
    case "cooling" => 1
    case "heating" => 2

    case "heat" => 1
    case "cool" => 2
    case "heat-cool" => 3

    case str: String => s"""\"$str\""""
    case _ => v
  }

  private[this] val protectMetrics =
    Seq(
      "battery_health",
      "co_alarm_state",
      "smoke_alarm_state",
      "is_online")

  private[this] val thermoMetrics =
    Seq(
      "humidity",
      "ambient_temperature_f",
      "hvac_mode",
      "hvac_state",
      "target_temperature_f",
      "target_temperature_high_f",
      "target_temperature_low_f",
      "has_leaf")

  private def process(store: Store): Future[Unit] =
    request() flatMap { r =>
      AsyncStream.fromReader(r.reader) foreach { case Buf.Utf8(body) =>
        body.split("\n") match {
          case Array("event: put", data) =>
            val rec = json.readValue[NestResult](data.drop(6))
            write(store, rec.data) onFailure println
          case _ => ()
        }
      }
    }

  private[this] val dataDefs = Seq(
    ("smoke_co_alarms", "protect", protectMetrics),
    ("thermostats", "thermostat", thermoMetrics))

  private def write(store: Store, data: Nest.Data): Future[Unit] = {
    val entries = dataDefs flatMap { case (field, name, metrics) =>
      data.get(field).toSeq flatMap { objs =>
        objs flatMap { case (_, info) =>
          val tags = Map("name" -> info.get("name").get, "type" -> name)
          metrics flatMap { metric =>
            info.get(metric) map { v => StoreEntry(metric, translate(v), tags) }
          }
        }
      }
    }
    println(entries)
    store.write(entries) respond println
  }

  private[this] val clients = new ConcurrentHashMap[URL, Service[Request, Response]]()
  private[this] val newClient = asJavaFunction { url: URL =>
    Http.client
      .withTls(url.getHost)
      .withStreaming(true)
      .newClient(destStr(url))
      .toService
  }

  private def request(urlStr: String = nestUrl()): Future[Response] = {
    val url = new URL(urlStr)

    val req = Request(url.getPath, "auth" -> nestAuth())
    req.accept = "text/event-stream"
    req.host = url.getHost

    clients.computeIfAbsent(url, newClient)(req) flatMap {
      case r if r.statusCode == 307 => request(r.location.get)
      case r if r.statusCode == 200 => Future.value(r)
      case r => Future.exception(new Exception("Can't handle " + r))
    }
  }
}
