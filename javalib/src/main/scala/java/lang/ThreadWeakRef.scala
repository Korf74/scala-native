package java.lang

import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue

class ThreadWeakRef(thread: Thread) extends WeakReference(thread, ThreadWeakRef.refQueue) {

  private var nativeAddr: scala.Long = 0L

  def setNativeAddr(newAddr: scala.Long): Unit = nativeAddr = newAddr

  def getNativeAddr: scala.Long = nativeAddr

}

object ThreadWeakRef {

  private val refQueue: ReferenceQueue[Thread] = new ReferenceQueue[Thread]()

  // TODO a revoir
  def poll: ThreadWeakRef = refQueue.poll

}
