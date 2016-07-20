package smick

import com.twitter.conversions.time._
import com.twitter.finagle.http.{ Request, Response }
import com.twitter.finagle.{ Http, Service }
import com.twitter.util.{ Future, Time, TimeFormat }
import java.net.URL
import java.text.SimpleDateFormat
import java.time.ZoneOffset

case class WattVisionRecord(time: Long, rate: Double)
case class WattVisionValue(t: String, v: Float)
case class WattVisionRep(units: String, data: List[WattVisionValue])

trait WattVision { self: SmickHome =>
  val wattVisionDest = flag("wattvision.dest", "https://www.wattvision.com/api/v0.2/elec", "base url for WattVision")
  val wattVisionFreq = flag("wattvision.req", 5.seconds, "update freq for WattVision")
  val wattVisionSensor = flag("wattvision.sensor", "sensor_id", "WattVision sensor")
  val wattVisionId = flag("wattvision.id", "id_str", "WattVision API ID")
  val wattVisionKey = flag("wattvision.key", "key_str", "WattVision API Key")

  private[this] val timeParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private[this] val timeFormat = new TimeFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def wattVisionLoop(store: Store): Future[Unit] =
    loopIt("wattVision", wattVisionFreq(), process(store))

  @volatile private[this] var lastTime = Time.now - 10.seconds

  @volatile private[this] var _url: URL = _
  private def url = if (_url != null) _url else {
    _url = new URL(wattVisionDest())
    _url
  }

  @volatile private[this] var _client: Service[Request, Response] = _
  private def client = if (_client != null) _client else {
    _client = Http.client.withTls(url.getHost).newClient(destStr(url)).toService
    _client
  }

  private def process(store: Store): Future[Unit] = {
    val thisTime = Time.now

    client(Request(url.getPath,
      "sensor_id" -> wattVisionSensor(),
      "api_id" -> wattVisionId(),
      "api_key" -> wattVisionKey(),
      "type" -> "rate",
      "start_time" -> timeFormat.format(lastTime),
      "end_time" -> timeFormat.format(thisTime)
    )) flatMap { r => write(store, r.contentString) } onSuccess { _ => lastTime = thisTime }
  }

  private def write(store: Store, str: String): Future[Unit] = {
    val wattRep = json.readValue[WattVisionRep](str)
    val points = wattRep.data map { case WattVisionValue(t, v) =>
      val time = Time.fromSeconds(timeParser.parse(t).toInstant.atOffset(ZoneOffset.UTC).toEpochSecond.toInt)
      StoreEntry("watts", v, time = Some(time.inNanoseconds))
    }

    store.write(points)
  }
}
