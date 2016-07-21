package smick

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.Http
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.util.DefaultTimer
import com.twitter.server.{ Closer, TwitterServer }
import com.twitter.util.{ Await, Duration, Future }
import java.net.{ InetSocketAddress, URL }

trait SmickHome extends TwitterServer with Closer {
  implicit val timer = DefaultTimer.twitter

  val json = new ObjectMapper with ScalaObjectMapper
  json.registerModule(DefaultScalaModule)

  val noLoop = flag("noLoop", false, "Do not start the updater loops")
  protected def loopIt[T](what: String, delay: Duration, f: => Future[T]): Future[Unit] =
    if (noLoop()) Future.Done else {
      def loop(): Future[Unit] =
        f onFailure(log.error(_, what)) transform(_ => Future.Done) delayed(delay) before loop()

      val loopHandle = loop()
      onExit { loopHandle.raise(Halt) }
      loopHandle
    }

  protected def destStr(url: URL): String = {
    val port = if (url.getPort < 0) url.getDefaultPort else url.getPort
    s"${url.getHost}:${port}"
  }
}

object Halt extends Exception

object Main extends SmickHome
  with InfluxDB
  with Nest
  with ObserverIP
  with Rainforest
{
  val httpAddr = flag("http.addr", new InetSocketAddress(8888), "Server bind addr")

  def main(): Unit = {
    val store = new InfluxStore

    val http = Http.serve(httpAddr(), (new HttpMuxer())
      .withHandler("/rainforest", rainforestMuxer(store)))

    val nest = nestLoop(store)
    val observer = observerLoop(store)

    Await.all(http, nest, observer)
  }
}
