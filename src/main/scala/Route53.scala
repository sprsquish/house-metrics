package smick

import com.amazonaws.auth._
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.route53._
import com.amazonaws.services.route53.model._
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.finagle.http._
import com.twitter.finagle.netty4.param.WorkerPool
import com.twitter.util.{ Future, Promise }
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.resolver.dns._
import io.netty.util.concurrent.{ Future => NFuture, FutureListener }
import java.net.{ InetAddress, URL }
import java.util.Collections

trait Route53 { self: SmickHome =>
  val route53Domains = flag("route53.domains", Map.empty[String, String], "Route53 domains to update.")
  val route53Key = flag("route53.key", "", "Route53 AWS Access Key")
  val route53Secret = flag("route53.secret", "", "Route53 AWS Secret")
  val route53IPCheckURL = flag("route53.ipCheckURL", "http://api.ipify.org/", "Route53 url used to get our public IP.")
  val route53Freq = flag("route53.freq", 1.minute, "Route53 polling frequency.")

  private val ipCheckURL = Lazy[URL](new URL(route53IPCheckURL()))

  private val ipClient = Lazy[Service[Request, Response]] {
    Http.newClient(destStr(ipCheckURL())).toService
  }

  private val resolver = Lazy[DnsNameResolver] {
    val evtLoop = Stack.Params.empty[WorkerPool].eventLoopGroup
    new DnsNameResolverBuilder(evtLoop.next)
      .channelType(classOf[NioDatagramChannel])
      .build()
  }

  private val awsClient = Lazy[AmazonRoute53Async] {
    AmazonRoute53AsyncClientBuilder.standard
      .withRegion("us-east-1")
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(route53Key(), route53Secret())))
      .build()
  }

  private def currentIP = {
    val req = RequestBuilder().url(ipCheckURL()).buildGet()
    ipClient()(req) map { res => InetAddress.getByName(res.contentString) }
  }

  private def resolve(domain: String): Future[InetAddress] = {
    val p = new Promise[InetAddress]
    resolver().resolve(domain).addListener(new FutureListener[InetAddress] {
      def operationComplete(f: NFuture[InetAddress]) =
        if (f.isSuccess) p.setValue(f.get) else p.setException(f.cause)
    })
    p
  }

  private def update(zone: String, domain: String, oldIP: String, newIP: String): Future[Unit] = {
    val p = new Promise[Unit]

    val rrSet = new ResourceRecordSet()
    rrSet.setName(domain)
    rrSet.setType(RRType.A)
    rrSet.setTTL(300L)

    val oldRR = Collections.singletonList(new ResourceRecord(oldIP))
    val newRR = Collections.singletonList(new ResourceRecord(newIP))

    val changes = new java.util.ArrayList[Change]()
    changes.add(new Change(ChangeAction.DELETE, rrSet.clone.withResourceRecords(oldRR)))
    changes.add(new Change(ChangeAction.CREATE, rrSet.clone.withResourceRecords(newRR)))
    val batch = new ChangeBatch(changes)
    batch.setComment("")

    val req = new ChangeResourceRecordSetsRequest(zone, batch)

    awsClient().changeResourceRecordSetsAsync(req,
      new AsyncHandler[ChangeResourceRecordSetsRequest, ChangeResourceRecordSetsResult] {
        def onError(e: Exception) = p.setException(e)
        def onSuccess(req: ChangeResourceRecordSetsRequest, rep: ChangeResourceRecordSetsResult) = p.setDone()
      }
    )

    p
  }

  def route53Loop(): Future[Unit] =
    loopIt("route53", route53Freq(), process())

  def process(): Future[Unit] = {
    currentIP flatMap { curIP =>
      Future.join {
        route53Domains().toSeq map { case (domain, zoneID) =>
          resolve(domain) flatMap {
            case res if res == curIP => Future.Done
            case res =>
              update(zoneID, domain, res.getHostAddress(), curIP.getHostAddress()) onFailure {
                case err: com.amazonaws.services.route53.model.InvalidInputException =>
                case _ => ()
              }
          }
        }
      }
    }
  }
}
