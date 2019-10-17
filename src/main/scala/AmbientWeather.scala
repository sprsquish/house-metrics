package smick

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.{ Duration, Future }
import java.net.URL

trait AmbientWeather { self: SmickHome =>
  val ambientWeatherMAC = flag("ambientWeather.mac", "", "Weather station MAC")
  val ambientWeatherAPI = flag("ambientWeather.api", "", "Ambient Weather API Key")
  val ambientWeatherApp = flag("ambientWeather.app", "", "Ambient Weather App Key")
  val ambientWeatherFreq = flag("ambientWeather.freq", 1.minute, "Ambient Weather polling frequency")

  def ambientWeatherLoop(store: Store): Future[Unit] =
    loopIt("ambientWeather", ambientWeatherFreq(), process(store))

  private[this] val url = Lazy[URL] {
    new URL(s"https://api.ambientweather.net/v1/devices/${ambientWeatherMAC()}?apiKey=${ambientWeatherAPI()}&applicationKey=${ambientWeatherApp()}&limit=1")
  }

  private[this] val client = Lazy[HttpSvc] {
    val _url = url()
    Http.client
      .withLabel("ambient-weather")
      .withTls(_url.getHost)
      .newClient(destStr(_url))
      .toService
  }

  private def process(store: Store): Future[Unit] =
    client()(RequestBuilder().url(url()).buildGet()) flatMap { res =>
      val recs = json.readValue[List[Map[String, Any]]](res.contentString)
      val entries = recs flatMap { data =>
        val ts = {
          val raw = data("dateutc").asInstanceOf[Long]
          val d = Duration.fromMilliseconds(raw)
          Some(d.inNanoseconds)
        }

        StoreEntry("indoor_temp", data("tempinf"), time = ts) ::
        StoreEntry("indoor_hum", data("humidityin"), time = ts) ::
        StoreEntry("indoor_press", data("baromabsin"), time = ts) ::
        StoreEntry("outdoor_temp", data("tempf"), time = ts) ::
        StoreEntry("outdoor_hum", data("humidity"), time = ts) ::
        StoreEntry("wind_dir", data("winddir"), time = ts) ::
        StoreEntry("wind_gust", data("windgustmph"), time = ts) ::
        StoreEntry("wind_speed", data("windspeedmph"), time = ts) ::
        StoreEntry("solar_radiation", data("solarradiation"), time = ts) ::
        StoreEntry("uv_index", data("uv"), time = ts) ::
        Nil
      }

      store.write(entries)
    }
}
