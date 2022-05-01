package smick

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.{ DefaultScalaModule, ClassTagExtensions }
import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.DurationOps._
import com.twitter.finagle._
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.http._
import com.twitter.finagle.stats.DefaultStatsReceiver
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.{ Buf, Reader }
import com.twitter.server.TwitterServer
import com.twitter.server.logging.{ Logging => JDK14Logging }
import com.twitter.util.{ Await, Duration, Future, FuturePool }
import java.net.URL
import java.net.{ InetSocketAddress, URL }
import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFun }

object Halt extends Exception

trait SmickHome extends TwitterServer with JDK14Logging {
  implicit val timer = DefaultTimer

  val json = JsonMapper.builder().addModule(DefaultScalaModule).build() :: ClassTagExtensions

  protected val loops = flag("loops", Set.empty[String], "Loops that should be enabled")

  protected def loopIt[T](what: String, delay: Duration, f: => Future[T]): Future[Unit] =
    if (loops().contains(what)) {
      def loop(): Future[Unit] =
        f onFailure(log.error(_, what)) transform(_ => Future.Done) delayed(delay) before loop()

      val loopHandle = loop()
      onExit { loopHandle.raise(Halt) }
      loopHandle
    } else {
      Future.Done
    }

  protected def destName(name: String, url: URL): Name = {
    val resolver = InetResolver(DefaultStatsReceiver.scope(name), Some(1.minute), FuturePool.unboundedPool)
    val port = if (url.getPort < 0) url.getDefaultPort else url.getPort
    val uri = s"${url.getHost}:${port}"
    Name.Bound(resolver.bind(uri), uri)
  }

  private[this] val eventStreamClients = new ConcurrentHashMap[(String, URL), Service[Request, Response]]()
  private[this] val newESClient: JFun[(String, URL), Service[Request, Response]] = { case ((name: String, url: URL)) =>
    Http.client
      .withTls(url.getHost)
      .withStreaming(true)
      .newClient(destName(name, url), name)
      .toService
  }

  protected def eventStream(
    name: String,
    url: String,
    params: (String, String)*
  )(f: PartialFunction[Event, Any]): Future[Unit] =
    eventStreamRequest(name, url, params) flatMap { r =>
      @volatile var event: Option[Event] = None
      Reader.toAsyncStream(r.reader) foreach { case Buf.Utf8(body) =>
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
    name: String,
    urlStr: String,
    params: Seq[(String, String)]
  ): Future[Response] = {
    val url = new URL(urlStr)

    val req = Request(url.getPath, params: _*)
    req.accept = "text/event-stream"
    req.host = url.getHost

    eventStreamClients.computeIfAbsent((name, url), newESClient)(req) flatMap {
      case r if r.statusCode == 307 =>
        Option(eventStreamClients.remove((name, url))) foreach { _.close() }
        eventStreamRequest(name, r.location.get, params)
      case r if r.statusCode == 200 => Future.value(r)
      case r => Future.exception(new Exception(s"Can't handle $r"))
    }
  }
}

object Main extends SmickHome
  with AmbientWeather
  with ApcUps
  with Awair
  with Binance
  with Flume
  with InfluxDB
  with Nest
  with ObserverIP
  with Particle
  with PurpleAir
  with Rachio
  with Rainforest
  with Route53
  with Weewx
{
  val httpAddr = flag("http.addr", new InetSocketAddress(8888), "Server bind addr")
  val shutdownAfter = flag("shutdownAfter", 4.hours, "Kill the process after this time")

  def main(): Unit = {
    println(s"SmickHome Metrics starting on ${httpAddr()}")
    println(s"  - Loops Enabled: ${loops().mkString(", ")}")

    val store = new InfluxStore

    val server = new HttpMuxer()
      .withHandler("/rainforest", rainforestMuxer(store))
      .withHandler("/rachio/webhook", rachioMuxer(store))
      .withHandler("/weewx", weewxMuxer(store))

    Future.sleep(shutdownAfter()) ensure {
      System.exit(0)
    }

    Await.all(
      ambientWeatherLoop(store),
      apcUpsLoop(store),
      awairLoop(store),
      binanceLoop(store),
      flumeLoop(store),
      nestLoop(store),
      observerLoop(store),
      particleLoop(store),
      purpleAirLoop(store),
      route53Loop(),
      Http.server
        .withLabel("main-hooks")
        .withHttpStats
        .serve(httpAddr(), server))
  }
}
