package scala.concurrent
package forkjoin

import java.io.Serializable
import java.util
import java.util.RandomAccess
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.lang.reflect.Constructor


abstract class ForkJoinTask[V] extends Future[V] with Serializable {

  // volatile
  var status: Int = _ // accessed directly by pool and workers


  private def setCompletion(completion: Int): Int = {
    var s: Int = _
    while(true) {
      s = status
      if(s < 0)
        return s
      if(U.comapreAndSwapInt(this, STATUS, s, s | completion)) {
        if((s >>> 16) != 0)
          this.synchronized(notifyAll())
        completion
      }
    }
  }

  final def doExec: Int = {
    var s: Int = status
    var completed: Boolean = false
    if (s >= 0) {
      try
        completed = exec
      catch {
        case rex: Throwable =>
          return setExceptionalCompletion(rex)
      }
      if (completed) s = setCompletion(NORMAL)
    }
    s
  }

  final def trySetSignal: Boolean = {
    val s: Int = status
    s >= 0 && U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)
  }

  private def externalAwaitDone: Int = {
    var s: Int = status
    ForkJoinPool.externalHelpJoin(this)
    var interrupted: Boolean = false
    while (s >= 0) {
      if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
        this.synchronized {
          if (status >= 0) {
            try
              wait()
            catch {
              case ie: InterruptedException =>
                interrupted = true
            }
          }
          else notifyAll()
        }

      }
      s = status
    }
    if (interrupted) Thread.currentThread.interrupt()
    s
  }

  private def externalInterruptibleAwaitDone: Int = {
    var s = status
    if (Thread.interrupted)
      throw new InterruptedException
    ForkJoinPool.externalHelpJoin(this)
    while (s >= 0) {
      if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
        this synchronized {
          if (status >= 0) wait()
          else notifyAll()
        }

      }
      s = status
    }
    s
  }

  // TODO line 339 doJoin

}

object ForkJoinTask {

  final val DONE_MASK: Int = 0xf0000000 // mask out non-completien bits
  final val NORMAL: Int = 0xf0000000 // must be negative
  final val CANCELLED: Int   = 0xc0000000;  // must be < NORMAL
  final val EXCEPTIONAL: Int = 0x80000000;  // must be < CANCELLED
  final val SIGNAL: Int      = 0x00010000;  // must be >= 1 << 16
  final val SMASK: Int = 0x0000ffff; // short bits for tags


}
