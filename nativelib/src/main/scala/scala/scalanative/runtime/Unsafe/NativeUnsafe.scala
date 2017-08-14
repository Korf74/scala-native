package scala.scalanative
package runtime.Unsafe

import native.{extern, CInt}
import posix.sys.types.pthread_t

@extern
object NativeUnsafe {

  def update_object(obj: Object, update: Object): Unit = extern

  def thd_continue(thread: pthread_t): CInt = extern

  def thd_suspend(thread: pthread_t): CInt = extern

}
