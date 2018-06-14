package smick

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.http.HttpMuxer
import com.twitter.io.Buf
import com.twitter.finagle.util.DefaultTimer
import com.twitter.server.TwitterServer
import com.twitter.server.logging.{ Logging => JDK14Logging }
import com.twitter.finagle.http._
import com.twitter.finagle.{ Http, Service }
import com.twitter.concurrent.AsyncStream
import com.twitter.util.{ Await, Duration, Future }
import java.net.{ InetSocketAddress, URL }
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

case class Event(kind: String, data: String)

case class Lazy[T](get: () => T) {
  @volatile var _value: Option[T] = None
  def apply(): T = _value getOrElse {
    _value = Some(get())
    _value.get
  }
}

trait SmickHome extends TwitterServer with JDK14Logging {
  implicit val timer = DefaultTimer

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

  private[this] val eventStreamClients = new ConcurrentHashMap[URL, Service[Request, Response]]()
  private[this] val newESClient: java.util.function.Function[URL, Service[Request, Response]] = { url: URL =>
    Http.client
      .withTls(url.getHost)
      .withStreaming(true)
      .newClient(destStr(url))
      .toService
  }

  protected def eventStream(
    url: String,
    params: Tuple2[String, String]*
  )(f: PartialFunction[Event, Any]): Future[Unit] =
    eventStreamRequest(url, params) flatMap { r =>
      @volatile var event: Option[Event] = None
      AsyncStream.fromReader(r.reader) foreach { case Buf.Utf8(body) =>
        body.split("\n") foreach {
          case line if line.startsWith("data: ") && event.isDefined =>
            val evt = event.get.copy(data = line.drop(6))
            event = None
            f.lift(evt)

          case line if line.startsWith("event: ") =>
            event = Some(Event(line.drop(7), ""))

          case _ => ()
        }
      }
    }

  private def eventStreamRequest(
    urlStr: String,
    params: Seq[Tuple2[String, String]]
  ): Future[Response] = {
    val url = new URL(urlStr)

    val req = Request(url.getPath, params: _*)
    req.accept = "text/event-stream"
    req.host = url.getHost

    eventStreamClients.computeIfAbsent(url, newESClient)(req) flatMap {
      case r if r.statusCode == 307 => eventStreamRequest(r.location.get, params)
      case r if r.statusCode == 200 => Future.value(r)
      case r => Future.exception(new Exception("Can't handle " + r))
    }
  }
}

object Halt extends Exception

object Main extends SmickHome
  with InfluxDB
  //with Nest
  //with ObserverIP
  with Particle
  with Rainforest
  with Rachio
  with Route53
  //with Weewx
  with AmbientWeather
{
  val httpAddr = flag("http.addr", new InetSocketAddress(8888), "Server bind addr")

  def main(): Unit = {
    val store = new InfluxStore

    val server = new HttpMuxer()
      .withHandler("/rainforest", rainforestMuxer(store))
      .withHandler("/rachio/webhook", rachioMuxer(store))
      //.withHandler("/weewx", weewxMuxer(store))

    Await.all(
      //nestLoop(store),
      //observerLoop(store),
      ambientWeatherLoop(store),
      particleLoop(store),
      route53Loop(),
      Http.server
        .withLabel("main-hooks")
        .withHttpStats
        .serve(httpAddr(), server))
  }
}
