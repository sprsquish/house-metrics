package smick

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.{ Duration, Future }
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

trait Binance { self: SmickHome =>
  val binanceFreq = flag("binance.freq", 1.second, "Binance.US polling frequency")
	val binanceApiKey = flag("binance.apiKey", "", "Binance.US API Key")
	val binanceSecretKey = flag("binance.secretKey", "", "Binance.US Secret Key")
  val binanceRecvWindow = flag("binance.recvWindow", 5000, "Binance.US valid receive window")

  def binanceLoop(store: Store): Future[Unit] =
    loopIt("binance", binanceFreq(), process(store))

  private[this] val pricesURL =
    new URL("https://api.binance.us/api/v3/ticker/price")

  private[this] val pricesClient = Lazy[HttpSvc] {
    val name = "binance-prices"
    Http.client
      .withTls(pricesURL.getHost)
      .newService(destName(name, pricesURL), name)
  }

  private def processPrices(store: Store): Future[Unit] =
    pricesClient()(RequestBuilder().url(pricesURL).buildGet()) flatMap { res =>
      val recs = json.readValue[List[Map[String, String]]](res.contentString)
      val entries = recs map { ticker =>
        StoreEntry(
          "binance.ticker",
          ticker("price").toDouble,
          tags = Map("symbol" -> ticker("symbol")))
      }

      store.write(entries)
    }

  private[this] val accountURL =
    new URL("https://api.binance.us/api/v3/account")

  private[this] val accountClient = Lazy[HttpSvc] {
    val name = "binance-account"
    Http.client
      .withTls(accountURL.getHost)
      .newService(destName(name, accountURL), name)
  }

  private[this] val secretKeySpec = Lazy[SecretKeySpec] {
    new SecretKeySpec(binanceSecretKey().getBytes, "HmacSHA256")
  }

	private def toHex(bytes: Array[Byte]): String = {
		val hexArray = "0123456789abcdef".toCharArray()
		val hexChars = new Array[Char](bytes.length * 2)
		(0 until bytes.length) foreach { j =>
			val v = bytes(j) & 0xFF
			hexChars(j * 2) = hexArray(v >>> 4)
			hexChars(j * 2 + 1) = hexArray(v & 0x0F)
		}
		new String(hexChars)
	}

  private[this] def sign(message: String): String = {
    val hmac256 = Mac.getInstance("HmacSHA256")
    hmac256.init(secretKeySpec())
    return new String(toHex(hmac256.doFinal(message.getBytes)))
  }

  private def processAccount(store: Store): Future[Unit] = {
    val ts = System.currentTimeMillis
    val query = s"recvWindow=${binanceRecvWindow()}&timestamp=$ts"
    val signature = sign(query)
    val req = RequestBuilder()
      .setHeader("X-MBX-APIKEY", binanceApiKey())
      .url(s"$accountURL?$query&signature=$signature")
      .buildGet()

    accountClient()(req) flatMap { res =>
      val acct = json.readValue[Map[String, Any]](res.contentString)
      val balances = acct("balances").asInstanceOf[List[Map[String, String]]]
      val entries = balances map { coin =>
        StoreEntry(
          "binance.balance",
          coin("free").toDouble,
          tags = Map("asset" -> coin("asset")))
      }

      store.write(entries)
    }
  }

  private def process(store: Store): Future[Unit] =
    Future.join(
      processPrices(store),
      processAccount(store)
    ).unit
}

