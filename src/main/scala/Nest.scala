package smick

import com.twitter.conversions.DurationOps._
import com.twitter.util.Future

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
    eventStream("nest", nestUrl(), "auth" -> nestAuth()) { case Event("put", data) =>
      val rec = json.readValue[NestResult](data)
      write(store, rec.data) onFailure println
    }

  private[this] val dataDefs = Seq(
    ("smoke_co_alarms", "protect", protectMetrics),
    ("thermostats", "thermostat", thermoMetrics))

  private def write(store: Store, data: Nest.Data): Future[Unit] = {
    val entries = for {
      (field, name, metrics) <- dataDefs
      objs <- data.get(field).toSeq
      (_, info) <- objs
      tags = Map("name" -> info.get("name").get, "type" -> name)
      metric <- metrics
      v <- info.get(metric)
    } yield StoreEntry(metric, translate(v), tags)

    store.write(entries)
  }
}
