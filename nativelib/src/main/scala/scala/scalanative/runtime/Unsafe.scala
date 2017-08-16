package scala.scalanative
package runtime

import native.{extern, name, CInt, stackalloc, CLong, Ptr}
import posix.sys.types.pthread_t

object Unsafe {

  private val atomicObject = CAtomicRef[Object]()

  import NativeUnsafe._

  private val lock: Object = new Object()

  def park(thread: Thread): Unit = thread.suspend()

  def unpark(thread: Thread): Unit = thread.resume()

  @extern
  private object NativeUnsafe {}

}