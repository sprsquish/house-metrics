package smick

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future

trait Weewx { self: SmickHome =>
  def weewxMuxer(store: Store): HttpSvc =
    Service.mk[Request, Response] { req =>
      val vals = json.readValue[Map[String, Double]](req.contentString)
      val entries = vals map { case (k, v) => StoreEntry(k, v) }
      store.write(entries.toSeq) map { _ => Response(req) }
    }
}
