package smick

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.{ Fields, MediaType, RequestBuilder }
import com.twitter.io.Buf
import com.twitter.util.{ Time, TimeFormat, Future }
import java.net.URL
import java.util.{ Base64, TimeZone }

case class FlumeTokenReq(
  grant_type: String,
  client_id: String,
  client_secret: String,
  username: String,
  password: String)

case class FlumeToken(token: String)

case class FlumeData(
  data: Double,
  ttl: Int,
  published_at: String,
  coreid: String)

case class FlumeQuery(
  since_datetime: String,
  until_datetime: String,
  request_id: String = "squishtech",
  bucket: String = "MIN",
  units: String = "GALLONS")

trait Flume { self: SmickHome =>
  val flumeClientID = flag("flume.clientID", "", "Flume oAuth client ID") // 182597VWEXZ7F88
  val flumeClientSecret = flag("flume.clientSecret", "", "Flume oAuth client secret") // D1D8Z32UU52W6K6X83AM
  val flumeUsername = flag("flume.username", "", "Flume username")
  val flumePassword = flag("flume.password", "", "Flume password")
  val flumeUserID = flag("flume.userID", "", "Flume user ID") // 18259
  val flumeDeviceID = flag("flume.deviceID", "", "Flume device ID") // 6634006660577838998

  private[this] val timeFormat = new TimeFormat(
    "yyyy-MM-dd HH:mm:ss",
    timezone = TimeZone.getTimeZone("America/Los_Angeles"))

  private[this] val authURL= new URL("https://api.flumetech.com/oauth/token")
  private[this] val authClient = Lazy[HttpSvc] {
    Http.client
      .withTls(authURL.getHost)
      .newService(destName(authURL), "flume-token")
  }

  private[this] val queryURL = Lazy[URL] {
    new URL(s"https://api.flumetech.com/users/${flumeUserID()}/devices/${flumeDeviceID()}/query")
  }

  private[this] val queryClient = Lazy[HttpSvc] {
    val url = queryURL()
    Http.client
      .withTls(url.getHost)
      .newService(destName(url), "flume-query")
  }

  private[this] val tokenBody = Lazy[Buf] {
    val tkn = FlumeTokenReq(
      "password",
      flumeClientID(),
      flumeClientSecret(),
      flumeUsername(),
      flumePassword())
    Buf.Utf8(json.writeValueAsString(tkn))
  }

  @volatile private[this] var token: FlumeToken = null
  @volatile private[this] var since: String = null

  def flumeLoop(store: Store): Future[Unit] =
    loopIt("flume", 1.minute, process(store))

  private def getToken(): Future[FlumeToken] = {
    val req = RequestBuilder()
      .url(authURL)
      .setHeader(Fields.ContentType, MediaType.Json)
      .buildPost(tokenBody())

    authClient()(req) map { res =>
      val resData = json.readValue[Map[String, Any]](res.contentString)
      val data = resData("data").asInstanceOf[List[Any]].head
      val t = data.asInstanceOf[Map[String, Any]]
      FlumeToken(t("access_token").asInstanceOf[String])
    }
  }

  private def mkNow(): String = timeFormat.format(Time.now)

  private def process(store: Store): Future[Unit] = {
    getToken() flatMap { case FlumeToken(token) =>
      val now = mkNow()
      if (since eq null) since = now
      val flumeReq = FlumeQuery(since, now)

      val body = Map("queries" -> List(flumeReq))

      val req = RequestBuilder()
        .url(queryURL())
        .setHeader(Fields.ContentType, MediaType.Json)
        .setHeader(Fields.Authorization, s"Bearer $token")
        .buildPost(Buf.Utf8(json.writeValueAsString(body)))

      queryClient()(req) flatMap { res =>
        val raw = json.readValue[Map[String, Any]](res.contentString)
        val entries = for {
          q <- raw("data").asInstanceOf[List[Map[String, Any]]]
          v <- q("squishtech").asInstanceOf[List[Map[String, Any]]]
        } yield {
          val ts = timeFormat.parse(v("datetime").asInstanceOf[String])
          StoreEntry(
            "flume.usage",
            v("value"),
            tags = Map("unit" -> "gallons"),
            time = Some(ts.inNanoseconds))
        }
        store.write(entries)
      }
    }
  }
}
