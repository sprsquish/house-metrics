package smick

import com.twitter.conversions.time._
import com.twitter.finagle.{ Http, Service }
import com.twitter.finagle.http._
import com.twitter.util.Future
import java.net.URL

trait AmbientWeather { self: SmickHome =>
  val ambientWeatherMAC = flag("ambientWeather.mac", "", "Weather station MAC")
  val ambientWeatherAPI = flag("ambientWeather.api", "", "Ambient Weather API Key")
  val ambientWeatherApp = flag("ambientWeather.app", "", "Ambient Weather App Key")
  val ambientWeatherFreq = flag("ambientWeather.freq", 2.seconds, "Ambient Weather polling frequency")

  def ambientWeatherLoop(store: Store): Future[Unit] =
    loopIt("ambientWeather", ambientWeatherFreq(), process(store))

  private[this] val url = Lazy[URL] {
    new URL(s"https://api.ambientweather.net/v1/devices/${ambientWeatherMAC()}?apiKey=${ambientWeatherAPI()}&applicationKey=${ambientWeatherApp()}")
  }

  private[this] val client = Lazy[Service[Request, Response]] {
    Http.newClient(destStr(url())).toService
  }

  private def process(store: Store): Future[Unit] =
    client()(RequestBuilder().url(url()).buildGet()) flatMap { res =>
      val recs = json.readValue[List[Map[String, Any]]](res.contentString)
      val entries = recs flatMap { data =>
        StoreEntry("indoor_temp", data("tempinf")) ::
        StoreEntry("indoor_hum", data("humidityin")) ::
        StoreEntry("indoor_press", data("baromabsin")) ::
        StoreEntry("outdoor_temp", data("tempf")) ::
        StoreEntry("outdoor_hum", data("humidity")) ::
        StoreEntry("wind_dir", data("winddir")) ::
        StoreEntry("wind_gust", data("windgustmph")) ::
        StoreEntry("wind_speed", data("windspeedmph")) ::
        StoreEntry("solar_radiation", data("solarradiation")) ::
        StoreEntry("uv_index", data("uv")) ::
        Nil
      }

      store.write(entries)
    }
}
