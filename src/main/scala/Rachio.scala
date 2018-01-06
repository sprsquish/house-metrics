package smick

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future

trait Rachio { self: SmickHome =>
  private def convert(status: Any): Int =
    status match {
      case "ZONE_COMPLETED" => 0
      case "started" => 1
      case _ => println(s"Rachio other status: $status"); -1
    }

  def rachioMuxer(store: Store): Service[Request, Response] =
    Service.mk[Request, Response] { req =>
      val rec = json.readValue[Map[String, Any]](req.contentString)
      val rep = for {
        d <- rec.get("eventDatas")
        data = d.asInstanceOf[List[Map[String, Any]]]
        sEntry <- data find { _.get("key") == Some("status") }
        status <- sEntry.get("convertedValue")
        nEntry <- data find { _.get("key") == Some("zoneName") }
        name <- nEntry.get("convertedValue")
        tags = Map("name" -> name.asInstanceOf[String])
      } yield store.write(Seq(StoreEntry("sprinklers", convert(status), tags)))

      rep.getOrElse(Future.Done) map { _ => Response(req) }
    }
}
