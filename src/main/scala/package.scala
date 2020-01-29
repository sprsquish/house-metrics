import com.twitter.finagle.{ Http, Service }
import com.twitter.finagle.http._

package object smick {
  type HttpSvc = Service[Request, Response]

  case class Event(kind: String, data: String)

  trait Lazy[T <: AnyRef] { def apply(): T }
  object Lazy {
    def apply[T <: AnyRef](get: => T) =

      new Lazy[T] {
        private[this] var _value: T = _
        def apply(): T =
          synchronized {
            if (_value eq null) _value = get
            _value
          }
      }
  }
}
