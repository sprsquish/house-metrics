import com.twitter.finagle.{ Http, Service }
import com.twitter.finagle.http._

package object smick {
  type HttpSvc = Service[Request, Response]

  case class Event(kind: String, data: String)

  trait Lazy[T] { def apply(): T }
  object Lazy {
    def apply[T](get: => T) =
      new Lazy[T] {
        @volatile var _value: Option[T] = None
        def apply(): T = _value getOrElse {
          _value = Some(get)
          _value.get
        }
      }
  }
}
