package scala.scalanative
package runtime.Unsafe

object Unsafe {

  //import NativeUnsafe._

  private val lock: Object = new Object()

  /*def compareAndSwapObject(obj: Object, expected: Object, update: Object): Boolean = lock.synchronized {
    if(expected != obj) return false

    update_object(obj, update)
    true
  }*/

  def park(thread: Thread): Unit = thread.suspend()

  def unpark(thread: Thread): Unit = thread.resume()

}