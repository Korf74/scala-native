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

  import ForkJoinTask._

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

  private def doJoin(): Int = {
    var s: Int = status
    var t: Thread = Thread.currentThread()
    var wt: ForkJoinWorkerThread = t.asInstanceOf[ForkJoinWorkerThread]
    var w: ForkJoinPool.WorkQueue = wt.workQueue
    if(s < 0)
      s
    else {
      s = doExec
      if(t.isInstanceOf[ForkJoinWorkerThread]) {
        if(w.tryUnpush(this) && s < 0)
          s
        else
          wt.pool.awaitJoin(w, this)
      } else externalAwaitDone
    }
  }

  private def doInvoke(): Int = {
    var s: Int = doExec
    var t: Thread = Thread.currentThread()
    var wt: ForkJoinWorkerThread = t.asInstanceOf[ForkJoinWorkerThread]
    if ((s = doExec) < 0)
      s
    else {
      if ((t = Thread.currentThread).isInstanceOf[Nothing])
        (wt = t.asInstanceOf[Nothing]).pool.awaitJoin(wt.workQueue, this)
      else externalAwaitDone
    }
  }

  // Exception table support

  final def recordExceptionalCompletion(ex: Throwable): Int = {
    var s: Int = status
    if(s >= 0) {
      val h: Int = System.identityHashCode(this)
      val lock: ReentrantLock = exceptionTableLock
      lock.lock()
      try {
        expungeStaleExceptions()
        val t: Array[ExceptionNode] = exceptionTable
        val i: Int = h & (t.length - 1)
        var e: ExceptionNode = t(i)
        while(true) {
          if(e == null) {
            t(i) = new ExceptionNode(this, ex, t(i))
            break
          }
          if(e.get() == this) // already present
            break
          e = e.next
        }
      } finally {
        lock.unlock()
      }
      s = setCompletion(EXCEPTIONAL)
    }
    s
  }

  private def setExceptionalCompletion(ex: Throwable): Int = {
    val s: Int = recordExceptionalCompletion(ex)
    if((s & DONE_MASK) == EXCEPTIONAL)
      internalPropagateException(ex)
    s
  }

  def internalPropagateException(ex: Throwable) = {}

  private def clearExceptionalCompletion(): Unit = {
    val h: Int = System.identityHashCode(this)
    val lock: ReentrantLock = exceptionTableLock
    lock.lock()
    try {
      val t: Array[ExceptionNode] = exceptionTable
      val i: Int = h & (t.length - 1)
      var e: ExceptionNode = t(i)
      var pred: ExceptionNode = null
      while(e != null) {
        val next: ExceptionNode = e.next
        if(e.get() == this) {
          if(pred == null)
            t(i) = next
          else
            pred.next = next
          break
        }
        pred = e
        e = next
      }
      expungeStaleExceptions()
      status = 0
    } finally {
      lock.unlock()
    }
  }

  // TODO line 515 getThrowableException

}

object ForkJoinTask {

  final val DONE_MASK: Int = 0xf0000000 // mask out non-completien bits
  final val NORMAL: Int = 0xf0000000 // must be negative
  final val CANCELLED: Int   = 0xc0000000;  // must be < NORMAL
  final val EXCEPTIONAL: Int = 0x80000000;  // must be < CANCELLED
  final val SIGNAL: Int      = 0x00010000;  // must be >= 1 << 16
  final val SMASK: Int = 0x0000ffff; // short bits for tags

  /**
    * Table of exceptions thrown by tasks, to enable reporting by
    * callers. Because exceptions are rare, we don't directly keep
    * them with task objects, but instead use a weak ref table.  Note
    * that cancellation exceptions don't appear in the table, but are
    * instead recorded as status values.
    *
    * Note: These statics are initialized below in static block.
    */
  private final val exceptionTable: Array[ExceptionNode] = _
  private final val exceptionTableLock: ReentrantLock = _
  private final val exceptionTableRefQueue: ReferenceQueue[Object] = _

  /**
    * Fixed capacity for exceptionTable.
    */
  private final val EXCEPTION_MAP_CAPACITY: Int = 32

  final class ExceptionNode(task: ForkJoinTask[_], ex: Throwable, next: ExceptionNode) extends WeakReference[ForkJoinTask[_]] {}

  final def cancelIgnoringExceptions(t: ForkJoinTask[_]): Unit = {
    if(t != null && t.status >= 0) {
      try {
        t.cancel(false)
      } catch {
        case ignore: Throwable =>
      }
    }
  }

}
