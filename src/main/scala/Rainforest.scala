package smick

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import scala.xml.XML

trait Rainforest { self: SmickHome =>
  def rainforestMuxer(store: Store): Service[Request, Response] =
    Service.mk[Request, Response] { req =>
      val doc = XML.loadString(req.contentString)
      ((doc \ "InstantaneousDemand" \ "Demand").headOption match {
        case None =>
          Future.Done
        case Some(v) =>
          try {
            val watts = java.lang.Long.decode(v.text).toInt
            store.write(Seq(StoreEntry("watts", watts)))
          } catch { case t: Throwable =>
            Future.Done
          }
      }) map { _ => Response(req) }
    }
}
