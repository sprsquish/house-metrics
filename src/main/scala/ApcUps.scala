package smick

import com.twitter.conversions.DurationOps._
import com.twitter.util.{ Future, FuturePool }
import io.netty.buffer.Unpooled
import java.io._
import java.net._

private case class StatusLine(label: String, value: String)

trait ApcUps { self: SmickHome =>
  val apcupsHost = flag("apcups.host", "localhost", "APC UPS host.")
  val apcupsPort = flag("apcups.port", 3551, "APC UPS port.")
  val apcupsFreq = flag("apcups.freq", 5.seconds, "APC UPS polling frequency.")

  private[this] val NullChar = 0x00.asInstanceOf[Char]

  private[this] val Status = {
    val arr = new Array[Byte](8)
    Unpooled.buffer(8)
      .writeShort(6)
      .writeBytes("status".getBytes)
      .readBytes(arr)
    arr
  }

  private def getStatus: Future[String] =
    FuturePool.unboundedPool {
      val sock = new Socket("localhost", 3551)
      try {
        val out = sock.getOutputStream
        out.write(Status)

        val in = sock.getInputStream
        new String(in.readAllBytes)
      } finally {
        sock.close()
      }
    }

  private def process(store: Store): Future[Unit] =
    getStatus flatMap { status =>
      val statLines = status.split(NullChar).toSeq flatMap { line =>
        line.split(':') match {
          case Array(rawLabel, rawValue) =>
            val label = rawLabel.drop(1).takeWhile(_ != ' ')
            val value = rawValue.dropRight(1)
            Some(StatusLine(label, value))
          case _ =>
            None
        }
      }

      def toFloat(s: String): Float =
        s.drop(1).takeWhile(_ != ' ').toFloat

      var load: Float = 0
      var power: Float = 0

      val entries = statLines collect {
        case StatusLine("BCHARGE", v) =>
          StoreEntry("apc.bcharge", toFloat(v))
        case StatusLine("TIMELEFT", v) =>
          StoreEntry("apc.timeleft", toFloat(v))
        case StatusLine("NOMPOWER", v) =>
          power = toFloat(v)
          StoreEntry("apc.nompower", toFloat(v))
        case StatusLine("LOADPCT", v) =>
          load = toFloat(v)
          StoreEntry("apc.load", toFloat(v))
      }

      val watts = StoreEntry("apc.watts", load * 0.01 * power)

      store.write(entries :+ watts)
    }

  def apcUpsLoop(store: Store): Future[Unit] =
    loopIt("apcups", apcupsFreq(), process(store))
}
