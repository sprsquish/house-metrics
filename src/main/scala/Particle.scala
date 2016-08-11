package smick

import com.twitter.conversions.time._
import com.twitter.util.Future

case class RoomTemp(
  data: Double,
  ttl: Int,
  published_at: String,
  coreid: String)

trait Particle { self: SmickHome =>
  val particleTempStream = flag("particle.stream.temp", "https://api.particle.io/v1/devices/events/temperature", "URL for temperture event stream")
  val particleAuth = flag("particle.auth", "", "Particle auth code")

  def particleLoop(store: Store): Future[Unit] =
    loopIt("particle", 1.minute, process(store))

  private def process(store: Store): Future[Unit] =
    eventStream(particleTempStream(), "access_token" -> particleAuth()) {
      case Event("temperature", data) =>
        val temp = json.readValue[RoomTemp](data)
        val entry = StoreEntry("room_temperature", temp.data, Map("coreid" -> temp.coreid))
        store.write(Seq(entry)) onFailure println
    }
}
