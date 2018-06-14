package smick

import com.twitter.conversions.time._
import com.twitter.finagle.{ Http, Service }
import com.twitter.finagle.http._
import com.twitter.util.Future
import java.net.URL

trait ObserverIP { self: SmickHome =>
  val observerDest = flag("observer.dest", "hostname:80", "Meteobridge dest")
  val observerFreq = flag("observer.freq", 10.seconds, "Meteobridge polling frequency")
  val observerUser = flag("observer.user", "username", "Meteobridge username")
  val observerPass = flag("observer.pass", "password", "Meteobridge password")

  def observerLoop(store: Store): Future[Unit] =
    loopIt("observer", observerFreq(), process(store))

  private[this] val url = Lazy[URL] {
    new URL(s"http://${observerUser()}:${observerPass()}@${observerDest()}/cgi-bin/livedata.cgi")
  }

  private[this] val client = Lazy[Service[Request, Response]] {
    Http.newClient(destStr(url())).toService
  }

  private def process(store: Store): Future[Unit] =
    client()(RequestBuilder().url(url()).buildGet()) flatMap { res =>
      val entries = res.contentString.split("\n") flatMap { line =>
        line.split(" ").toList match {
          case _ :: "thb0" :: temp :: hum :: _ :: press :: _ =>
            Seq(StoreEntry("indoor_temp", temp),
                StoreEntry("indoor_hum", hum),
                StoreEntry("indoor_press", press))

          case _ :: "th0" :: temp :: hum :: _ =>
            Seq(StoreEntry("outdoor_temp", temp),
                StoreEntry("outdoor_hum", hum))

          case _ :: "wind0" :: dir :: gust :: speed :: _ =>
            Seq(StoreEntry("wind_dir", dir),
                StoreEntry("wind_gust", gust),
                StoreEntry("wind_speed", speed))

          case _ :: "sol0" :: rad :: _ =>
            Seq(StoreEntry("solar_radiation", rad))

          case _ :: "uv0" :: index :: _ =>
            Seq(StoreEntry("uv_index", index))

          case _ :: "rain0" :: rate :: total :: _ =>
            Seq(StoreEntry("rain_rate", rate),
                StoreEntry("rain_total", total))

          case _ =>
            Seq.empty
        }
      }

      store.write(entries)
    }
}
