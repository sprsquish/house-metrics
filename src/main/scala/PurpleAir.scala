package smick

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Future
import java.net.URL

case class PurpleAirData(
  mapVersion: String,
  baseVersion: String,
  mapVersionString: String,
  results: List[Map[String, Any]])

trait PurpleAir { self: SmickHome =>

  val purpleAirFreq = flag("purpleair.freq", 10.seconds, "Purple Air polling frequency")
  val purpleAirSensor = flag("purpleair.sensor", 0, "Purple Air sensor ID")

  def purpleAirLoop(store: Store): Future[Unit] =
    loopIt("purpleAir", purpleAirFreq(), process(store))

  private[this] val url = Lazy[URL] {
    new URL(s"https://www.purpleair.com/json?show=${purpleAirSensor()}")
  }

  private[this] val client = Lazy[HttpSvc] {
    val name = "purple-air"
    Http.client
      .withTls(url().getHost)
      .newService(destName(name, url()), name)
  }

  private[this] val sensorMetrics =
    Seq(
      "p_0_3_um",
      "p_0_5_um",
      "p_1_0_um",
      "p_2_5_um",
      "p_5_0_um",
      "p_10_0_um",
      "pm1_0_cf_1",
      "pm2_5_cf_1",
      "pm10_0_cf_1",
      "pm1_0_atm",
      "pm2_5_atm",
      "pm10_0_atm",
      "humidity",
      "temp_f",
      "pressure")

  private def process(store: Store): Future[Unit] =
    client()(RequestBuilder().url(url()).buildGet()) flatMap { res =>
      val data = json.readValue[PurpleAirData](res.contentString)

      val entries = for {
        result <- data.results
        tags = Map("label" -> result.get("Label").get)
        metric <- sensorMetrics
        v <- result.get(metric)
      } yield StoreEntry(metric, v, tags)

      store.write(entries)
    }
}
