package smick

import com.twitter.conversions.DurationOps._
import com.twitter.util.Future

case class ParticleData(
  data: Double,
  ttl: Int,
  published_at: String,
  coreid: String)

trait Particle { self: SmickHome =>
  val particleStream = flag("particle.stream", "https://api.particle.io/v1/devices/events", "URL for event stream")
  val particleAuth = flag("particle.auth", "", "Particle auth code")

  def particleLoop(store: Store): Future[Unit] =
    loopIt("particle", 1.minute, process(store))

  private def process(store: Store): Future[Unit] = {
    val evts = Set("lum-vis", "lum-full", "lum-ir")

    eventStream("particle", particleStream(), "access_token" -> particleAuth()) {
      case Event(evt, jsonStr) if evts contains evt =>
        val ParticleData(value, _, _, coreid)= json.readValue[ParticleData](jsonStr)
        val entry = StoreEntry(s"particle.$evt", value, Map("coreid" -> coreid))
        store.write(Seq(entry)) onFailure println
    }
  }
}
