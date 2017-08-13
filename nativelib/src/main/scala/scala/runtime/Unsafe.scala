package scala.scalanative
package runtime

import native.extern
import posix.sys.types.pthread_t

object Unsafe {

  import nativeUnsafe._

  private val lock: Object = new Object()

  def compareAndSwapObject(obj: Object, expected: Object, update: Object): Boolean = lock.synchronized {
    if(expected != obj) return false

    update_object(obj, update)
    true
  }

  def park(thread: Thread): Unit = {
    thread.getPID match {
      case None => // Do nothing, thread hasn't started yet
      case Some(id: pthread_t) =>
        val status = thd_suspend(id)
        if(status != 0) throw new RuntimeException("Error while trying to park thread "+ thread)
    }
  }

  def unpark(thread: Thread): Unit = {
    thread.getPID match {
      case None => // Do nothing, thread hasn't started yet
      case Some(id: pthread_t) =>
        val status = thd_continue(id)
        if(status != 0) throw new RuntimeException("Error while trying to unpark thread "+ thread)
    }
  }



  @extern
  private object nativeUnsafe {

    def update_object(obj: Object, update: Object): Unit = extern

    def thd_continue(thread: pthread_t): Int = extern

    def thd_suspend(thread: pthread_t): Int = extern

  }

}
