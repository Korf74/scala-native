package scala.scalanative
package runtime

import native.{extern, name, CInt}
import posix.sys.types.pthread_t

object Unsafe {

  import NativeUnsafe._

  private val lock: Object = new Object()

  def compareAndSwapObject(obj: Object, expected: Object, update: Object): Boolean = lock.synchronized {
    if (!expected.equals(update)) return false

    updateObject(obj, update)
    true
  }

  def park(thread: Thread): Unit = thread.suspend()

  def unpark(thread: Thread): Unit = thread.resume()

  @extern
  private object NativeUnsafe {

    @name("update_object")
    def updateObject(obj: Object, update: Object): Unit = extern

  }
}