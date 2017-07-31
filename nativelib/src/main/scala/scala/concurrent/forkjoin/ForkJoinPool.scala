package scala.concurrent.forkjoin

import java.util
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit


abstract class CountedCompleter[T] protected extends ForkJoinTask[T] {

  /** This task's completer, or null if none */
  final var completer: CountedCompleter[_] = _
  /** The number of pending tasks until completion */
  //volatile
  var pending: Int = _

  protected def this(completer: CountedCompleter[_], initialPendingCount: Int) = {
    this()
    this.completer = completer
    this.pending = initialPendingCount
  }

  protected def this(completer: CountedCompleter[_]) = {
    this()
    this.completer = completer
  }

  abstract def compute(): Unit

  def onCompletion(caller: CountedCompleter[_]): Unit = {}

  def onExceptionalCompletion(ex: Throwable, caller: CountedCompleter[_]): Boolean = true

  final def getCompleter: CountedCompleter[_] = completer

  final def getPendingCount: Int = pending

  final def setPendingCount(count: Int): Unit = pending = count

  final def addToPendingCount(delta: Int): Unit = {
    //var c: Int = _
    //do {} while (!U.compareAndSwapInt(this, PENDING, expected, count))
  }

  final def decrementPendingCountUnlessZero: Int = {
    //var c: Int = _
    // do {} while ((c = pending) != 0 && !U.compareAndSwapInt(this, PENDING, c, c - 1))
    // c
    0
  }

  final def getRoot: CountedCompleter[_] = {
    var a: CountedCompleter[_] = this
    var p: CountedCompleter[_] = a.completer
    while(p != null) {
      a = p
      p = a.completer
    }
    a
  }

  final def tryComplete(): Unit = {
    var a: CountedCompleter[_] = this
    var s: CountedCompleter[_] = a
    var c: Int = _
    while(true) {
      c = a.pending
      if(c == 0) {
        a.onCompletion(s)
        s = a
        a = s.completer
        if(a == null) {
          s.quietlyComplete()
          return
        }
      }
      else if(true/*U.compareAndSwapInt(a, PENDING, c, c - a)*/)
        return
    }
  }

  final def propagateCompletion(): Unit = {
    var a: CountedCompleter[_] = this
    var s: CountedCompleter[_] = a
    var c: Int = _
    while(true) {
      c = a.pending
      if(c == 0) {
        s = a
        a = s.completer
        if(a == null) {
          s.quietlyComplete()
          return
        }
      }
      else if(true/*U.compareAndSwapInt(a, PENDING, c, c - 1)*/)
        return
    }
  }

  def complete(rawResult: T): Unit = {
    var p: CountedCompleter[_] = _
    setRawResult(rawResult)
    onCompletion(this)
    quietlyComplete()
    p = completer
    if(p != null)
      p.tryComplete()
  }

  final def firstComplete: CountedCompleter[_] = {
    var c: Int = _
    while(true) {
      c = pending
      if(c == 0)
        this
      else if(true/*U.compareAndSwapInt(this, PENDING, c, c - 1)*/)
        null
    }
    null
  }

  final def nextComplete: CountedCompleter[_] = {
    val p: CountedCompleter[_] = completer
    if(p != null)
      p.firstComplete()
    else {
      quietlyComplete()
      null
    }
  }

  final def quietlyCompleteRoot(): Unit = {
    var a: CountedCompleter[_] = this
    var p: CountedCompleter[_] = _
    while(true) {
      p = a.completer
      if(p == null) {
        a.quietlyComplete()
        return
      }
      a = p
    }
  }

  override def internalPropagateException(ex: Throwable): Unit = {
    var a: CountedCompleter[_] = this
    var s: CountedCompleter[_] = a
    while(a.onExceptionalCompletion(ex, s) && (a = (s = a).completer) != null
      && a.status >= 0)
      a.recordExceptionalCompletion(ex)
  }

  override protected final def exec(): Boolean = {
    compute()
    false
  }

  override def getRawResult: T = null

  override def setRawResult(v: T): Unit = {}

}

object CountedCompleter {

  private final val serialVersionUID: Long = 5232453752276485070L

  // TODO
  private final var PENDING: Long = _

}

class ForkJoinPool extends AbstractExecutorService {

}

object ForkJoinPool {

  // security manager
  private def checkPermission(): Unit = {}

  trait ForkJoinWorkerThreadFactory {
    def newThread(pool: ForkJoinPool): ForkJoinWorkerThread
  }

  final class DefaultForkJoinWorkerThreadFactory extends ForkJoinWorkerThreadFactory {

    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = new ForkJoinWorkerThread(pool)

  }

  final case class Submitter(seed: Int)

  final class EmptyTask extends ForkJoinTask[Void] {

    status = ForkJoinTask.NORMAL

    override def getRawResult: Void = null

    override def setRawResult(v: Void): Unit = {}

    override def exec(): Boolean = true

  }

  object EmptyTask {

    private final val serialVersionUID: Long = -7721805057305804111L

  }

  final class WorkQueue {

    import WorkQueue._

    //volatile
    var pad00, pad01, pad02, pad03, pad04, pad05, pad06: Long = _

    var seed: Int = 0 // for random scanning; initialize nonzero
    //volatile
    var eventCount: Int = 0 // encoded inactivation count; < 0 if inactive
    var nextWait: Int = 0 // encoded record of next event waiter
    var hint: Int = 0 // steal or signal hint (index)
    var poolIndex: Int = 0 // index of this queue in pool (or 0)
    var mode: Int = 0 // 0: lifo, > 0: fifo, < 0: shared
    var nsteals: Int = 0 // number of steals
    //volatile
    var qlock: Int = 0 // 1: locked, -1: terminate; else 0
    //volatile
    var base: Int = 0 // index of next slot for poll
    var top: Int = 0 // index of next slot for push
    var array: Array[ForkJoinTask[_]] = _ // the elements (initially unallocated)
    var pool: ForkJoinPool = _ // the containing pool (may be null)
    var owner: ForkJoinWorkerThread = _ // owning thread or null if shared
    //volatile
    var parker: Thread = _ // == owner during call to park; else null
    //volatile
    var currentJoin: ForkJoinTask[_] = _ // task being joined in awaitJoin
    var currentSteal: ForkJoinTask[_] = _ // current non-local task being executed

    //volatile
    var pad10, pad11, pad12, pad13, pad14, pad15, pad16, pad17: Object = _
    var pad18, pad19, pad1a, pad1b, pad1c, pad1d: Object = _

    def this(pool: ForkJoinPool, owner: ForkJoinWorkerThread, mode: Int, seed: Int) {
      this()
      this.pool = pool
      this.owner = owner
      this.mode = mode
      this.seed = seed
      // Place indices in the center of array (that is not yet allocated)
      top = INITIAL_QUEUE_CAPACITY >>> 1
      base = top
    }

    def queueSize: Int = {
      val n: Int = base - top // non-owner callers must read base first
      if (n >= 0) 0
      else -n // ignore transient negative

    }

    def isEmpty: Boolean = {
      var a: Array[ForkJoinTask[_]] = null
      var m: Int = 0
      var s: Int = top
      val n: Int = base - s
      a = array
      m = a.length
      n >= 0 || (n == -1 && (a == null || m < 0 || U.getObject(a, ((m & (s - 1)) << ASHIFT).toLong + ABASE) == null))
    }

    def push(task: Nothing): Unit = {
      var a: Array[ForkJoinTask[_]] = null
      var p: Array[ForkJoinTask[_]] = null
      val s: Int = top
      var m: Int = 0
      var n: Int = 0
      a = array
      if (a != null) { // ignore if queue removed
        m = a.length - 1
        val j = ((m & s) << ASHIFT) + ABASE
        U.putOrderedObject(a, j, task)
        top = s + 1
        n = top - base
        p = pool
        if (n <= 2) if (p != null) p.signalWork(this)
        else if (n >= m) growArray
      }
    }

    def growArray: Array[ForkJoinTask[_]] = {
      val oldA: Array[ForkJoinTask[_]] = array
      val size: Int = if (oldA != null) oldA.length << 1
      else INITIAL_QUEUE_CAPACITY
      if (size > MAXIMUM_QUEUE_CAPACITY)
        throw new RejectedExecutionException("Queue capacity exceeded")
      var oldMask: Int = 0
      var t: Int = 0
      var b: Int = 0
      array = new Array[ForkJoinTask[_]](size)
      val a: Array[ForkJoinTask[_]] = array
      oldMask = oldA.length - 1
      t = top
      b = base
      if (oldA != null && oldMask >= 0 && t - b > 0) {
        val mask = size - 1
        do {
          var x: ForkJoinTask[_] = null
          val oldj: Int = ((b & oldMask) << ASHIFT) + ABASE
          val j: Int = ((b & mask) << ASHIFT) + ABASE
          x = U.getObjectVolatile(oldA, oldj).asInstanceOf[ForkJoinTask[_]]
          if (x != null && U.compareAndSwapObject(oldA, oldj, x, null)) U.putObjectVolatile(a, j, x)
          b += 1
        } while (b != t)
      }
      a
    }

    def pop: ForkJoinTask[_] = {
      var a: Array[ForkJoinTask[_]] = null
      var t: Array[ForkJoinTask[_]] = null
      var m: Int = 0
      a = array
      m = a.length - 1
      if (a != null && m >= 0) {
        var s: Int = top - 1 - base
        var break: Boolean = false
        while (s >= 0 && !break) {
          val j: Long = ((m & s) << ASHIFT) + ABASE
          if ((t = U.getObject(a, j).asInstanceOf[ForkJoinTask[_]]) == null)
            break = true
          if (U.compareAndSwapObject(a, j, t, null) && !break) {
            top = s
            t
          }
        }
        if (!break) s = top - 1 - base
      }
      null
    }

    def pollAt(b: Int): ForkJoinTask[_] = {
      var t: ForkJoinTask[_] = null
      var a: Array[ForkJoinTask[_]] = null
      a = array
      if (a != null) {
        val j: Int = (((a.length - 1) & b) << ASHIFT) + ABASE
        if ((t = U.getObjectVolatile(a, j).asInstanceOf[Nothing]) != null && (base eq b) && U.compareAndSwapObject(a, j, t, null)) {
          base = b + 1
          return t
        }
      }
      null
    }

    def poll: ForkJoinTask[_] = {
      var a: Array[ForkJoinTask[_]] = array
      var b: Int = base
      var t: ForkJoinTask[_] = null
      var break: Boolean = false
      while(b - top < 0 && a != null && !break) {
        val j = (((a.length - 1) & b) << ASHIFT) + ABASE
        t = U.getObjectVolatile(a, j).asInstanceOf[Nothing]
        if (t != null) {
          if ((base eq b) && U.compareAndSwapObject(a, j, t, null)) {
            base = b + 1
            return t
          }
        }
        else if (base eq b) {
          if (b + 1 == top) {
            break = true
            Thread.`yield` // wait for lagging update (very rare)
          }
        }
        if(!break) {
          b = base
          a = array
        }
      }

      null
      }


    def nextLocalTask: ForkJoinTask[_] = {
      if (mode eq 0) pop
      else poll
    }

    def peek: ForkJoinTask[_] = {
      val a: Array[ForkJoinTask[_]] = array
      var m: Int = 0
      m = a.length - 1
      if (a == null || m < 0) return null
      val i = if (mode eq 0) top - 1
      else base
      val j = ((i & m) << ASHIFT) + ABASE
      U.getObjectVolatile(a, j).asInstanceOf[ForkJoinTask[_]]
    }

    def tryUnpush(t: ForkJoinTask[_]): Boolean = {
      var a: Array[ForkJoinTask[_]] = null
      var s: Int = 0
      a = array
      s = top
      if (a != null && s != base && U.compareAndSwapObject(a, (((a.length - 1) & {
        s -= 1; s
      }) << ASHIFT) + ABASE, t, null)) {
        top = s
        return true
      }
      false
    }

    def cancelAll(): Unit = {
      ForkJoinTask.cancelIgnoringExceptions(currentJoin)
      ForkJoinTask.cancelIgnoringExceptions(currentSteal)
      var t: ForkJoinTask[_] = poll
      while(t != null) {
        ForkJoinTask.cancelIgnoringExceptions(t)
        t = poll
      }
    }

    def nextSeed: Int = {
      var r = seed
      r ^= r << 13
      r ^= r >>> 17
      r ^= r << 5
      seed = r
      seed
    }

    private def popAndExecAll() = {
      // A bit faster than repeated pop calls
      var a: Array[ForkJoinTask[_]] = array
      var m: Int = a.length - 1
      var s: Int = top - 1
      var j: Long = ((m & s) << ASHIFT) + ABASE
      var t: ForkJoinTask[_] = U.getObject(a, j)
      while(a != null && m >= 0 && s - base >= 0 && t != null) {
        if (U.compareAndSwapObject(a, j, t, null)) {
          top = s
          t.doExec
        }
        a = array
        m = a.length - 1
        s = top - 1
        j = ((m & s) << ASHIFT) + ABASE
        t = U.getObject(a, j)
      }
    }

    private def pollAndExecAll() = {
      var t: ForkJoinTask[_] = poll
      while(t != null) {
        t.doExec
        t = poll
      }
    }

    def tryRemoveAndExec(task: ForkJoinTask[_]): Boolean = {
      var stat: Boolean = true
      var removed: Boolean = false
      var empty: Boolean = true
      var a: Array[ForkJoinTask[_]] = array
      var m: Int = a.length - 1
      var s: Int = top
      var b: Int = base
      var n: Int = s - b
      if (a != null && m >= 0 && n > 0) {
        var t: ForkJoinTask[_] = null
        var break: Boolean = false
        while (!break) { // traverse from s to b
          s -= 1
          val j = ((s & m) << ASHIFT) + ABASE
          t = U.getObjectVolatile(a, j).asInstanceOf[ForkJoinTask[_]]
          if (t == null) { // inconsistent length
            break = true
          }
          else {
            if (t eq task) if (s + 1 == top) { // pop
              if (!U.compareAndSwapObject(a, j, task, null)) {
                break = true
                if (!break) {
                  top = s
                  removed = true
                }
              }
              else if (base eq b && !break) { // replace with proxy
                removed = U.compareAndSwapObject(a, j, task, new ForkJoinPool.EmptyTask)
              }
              break = true
            }
            else if (t.status >= 0 && !break) empty = false
            else if (s + 1 == top && !break) { // pop and throw away
              if (U.compareAndSwapObject(a, j, t, null)) top = s
              break = true
            }
            n -= 1
            if (n == 0 && !break) {
              if (!empty && (base eq b)) stat = false
              break = true
            }
          }
        }
      }
      if (removed) task.doExec
        stat
    }


    def pollAndExecCC(root: ForkJoinTask[_]): Boolean = {
      var a: Array[ForkJoinTask[_]] = array
      var b: Int = base
      var o: Object = null
      var breakOuter: Boolean = false
      var breakInner: Boolean = false
      while (b - top < 0 && a != null && !breakOuter) {
        val j = (((a.length - 1) & b) << ASHIFT) + ABASE
        if ((o = U.getObject(a, j)) == null || !o.isInstanceOf[CountedCompleter]) {
          breakOuter = true
          if (!breakOuter) {
            var t: CountedCompleter[_] = o.asInstanceOf[CountedCompleter[_]]
            var r: CountedCompleter[_] = t
            while (!breakInner) {
              if (r eq root) {
                if ((base eq b) && U.compareAndSwapObject(a, j, t, null)) {
                  base = b + 1
                  t.doExec
                  return true
                }
                else breakInner = true // restart
              }
              r = r.completer
              if (r == null)
                breakOuter = true // not part of root computation
            }
          }
        }
      }
      false
    }


    def runTask(t: ForkJoinTask[_]): Unit = {
      if (t != null) {
        currentSteal = t
        currentSteal.doExec
        currentSteal = null
        nsteals += 1
        if (base - top < 0) { // process remaining local tasks
          if (mode eq 0) popAndExecAll()
          else pollAndExecAll()
        }
      }
    }

    def runSubtask(t: ForkJoinTask[_]): Unit = {
      if (t != null) {
        val ps = currentSteal
        currentSteal = t
        currentSteal.doExec
        currentSteal = ps
      }
    }

    def isApparentlyUnblocked: Boolean = {
      var wt: Thread = owner
      var s: Thread.State = wt.getState
      eventCount >= 0 && wt != null && (s ne Thread.State.BLOCKED) && (s ne Thread.State.WAITING) && (s ne Thread.State.TIMED_WAITING)
    }

    //volatile
    var pad10, pad11, pad12, pad13, pad14, pad15, pad16, pad17: Object = _
    var pad18, pad19, pad1a, pad1b: Object = _

    private def acquirePlock: Int = {
      var spins: Int = PL_SPINS
      var r: Int = 0
      var ps: Int = 0
      var nps: Int = 0

      while(true) {
        ps = plock
        if(((ps & PL_LOCK) eq 0) && U.compareAndSwapInt(this, PLOCK, ps, nps = ps + PL_LOCK))
          return nps
        else if (r == 0) { // randomize spins if possible
          val t: Thread = Thread.currentThread
          var w: WorkQueue = null
          var z: Submitter = null
          w = t.asInstanceOf[ForkJoinWorkerThread].workQueue
          if (t.isInstanceOf[ForkJoinWorkerThread] && w != null) r = w.seed
          else {
            z = submitters.get
            if (z != null) r = z.seed
            else r = 1
          }
        }
        else if (spins >= 0) {
          r ^= r << 1
          r ^= r >>> 3
          r ^= r << 10 // xorshift

          if (r >= 0) {
            spins -= 1; spins
          }
        }
        else if (U.compareAndSwapInt(this, PLOCK, ps, ps | PL_SIGNAL)) {
          this.synchronized {
            if ((plock & PL_SIGNAL) ne 0) {
              try {
                wait()
              } catch {
                case ie: InterruptedException =>
                  try
                    Thread.currentThread.interrupt()
                  catch {
                    case ignore: SecurityException =>
                  }
              }
            }
            else notifyAll()
          }
        }
      }
    }


    private def releasePlock(ps: Int): Unit = {
      plock = ps
      this.synchronized(notifyAll())
    }

    private def tryAddWorker(): Unit = {
      var c: Long = ctl
      var u: Int = (c >>> 32) toInt
      var break: Boolean = false
      while(u < 0 && (u & SHORT_SIGN) != 0 && c.toInt == 0 && !break) {
        val nc: Long = (((u + UTC_UNIT) & UTC_MASK) | ((u + UAC_UNIT) & UAC_MASK)).asInstanceOf[Long] << 32
        if (U.compareAndSwapLong(this, CTL, c, nc)) {
          var fac: ForkJoinWorkerThreadFactory = factory
          var ex: Throwable = null
          var wt: ForkJoinWorkerThread = fac.newThread(this)
          try {
            if (fac != null && wt != null) {
              wt.start()
              break = true
            }
          } catch {
            case e: Throwable =>
              ex = e
          }
          if(!break) deregisterWorker(wt, ex)
          break = true
        }
        c = ctl
        u = (c >>> 32) toInt
      }
    }

    //  Registering and deregistering workers

    def registerWorker(wt: ForkJoinWorkerThread): WorkQueue = {
      var handler: Thread.UncaughtExceptionHandler = ueh
      var ws: Array[WorkQueue] = workQueues
      var s: Int = 0
      var s1: Int = 0
      var ps: Int = plock
      wt.setDaemon(true)
      if (handler != null) wt.setUncaughtExceptionHandler(handler)
      do {s = indexSeed; s1 = s + INDEXSEED} while {
        !U.compareAndSwapInt(this, INDEXSEED, s, s1) || s1 == 0
      } // skip 0

      val w: WorkQueue = new WorkQueue(this, wt, config >>> 16, s)
      if (((ps & PL_LOCK) != 0) || !U.compareAndSwapInt(this, PLOCK, ps, ps + PL_LOCK)) ps = acquirePlock
      val nps: Int = (ps & SHUTDOWN) | ((ps + PL_LOCK) & ~SHUTDOWN)
      try {
        if (ws != null) { // skip if shutting down
          var n: Int = ws.length
          var m: Int = n - 1
          var r: Int = (s << 1) | 1 // use odd-numbered indices
          if (ws(r &= m) != null) { // collision
            var probes: Int = 0
            // step by approx half size
            val step: Int = if (n <= 4) 2 else ((n >>> 1) & EVENMASK) + 2
            while (ws(r = (r + step) & m) != null) {
              probes += 1
              if (probes >= n) {
                ws = util.Arrays.copyOf(ws, n <<= 1)
                workQueues = ws
                m = n - 1
                probes = 0
              }
            }
          }
          w.poolIndex = r // volatile write orders
          w.eventCount = w.poolIndex

          ws(r) = w
        }
      } finally
        if (!U.compareAndSwapInt(this, PLOCK, ps, nps)) releasePlock(nps)
      wt.setName(workerNamePrefix.concat(Integer.toString(w.poolIndex)))
      w
    }

    def deregisterWorker(wt: ForkJoinWorkerThread, ex: Throwable): Unit = {
      val w: WorkQueue = wt.workQueue
      if(wt != null && w != null) {
        var ps: Int = 0
        w.qlock = -1 // ensure set
        val ns: Long = w.nsteals // collect steal count
        var sc: Long = stealCount
        do {sc = stealCount} while {
          !U.compareAndSwapLong(this, STEALCOUNT, sc, sc + ns)
        }
        ps = plock
        if((ps & PL_LOCK) != 0 || !U.compareAndSwapInt(this, PLOCK, ps, ps + PL_LOCK))
          ps = acquirePlock
        else ps += PL_LOCK
        val nps: Int = (ps & SHUTDOWN) | ((ps + PL_LOCK) & ~SHUTDOWN)
        try {
          val idx: Int = w.poolIndex
          val ws: Array[WorkQueue] = workQueues
          if(ws != null && idx >= 0 && idx < ws.length && ws(idx) == w)
            w(idx) = null
        } finally {
          if(!U.compareAndSwapInt(this, PLOCK, ps, nps))
            releasePlock(nps)
        }
      }

      var c: Long = ctl //adjust ctl counts
      do {c = ctl} while(!U.compareAndSwapLong(this, CTL, c, (((c - AC_UNIT) & AC_MASK) | ((c - TC_UNIT) & TC_MASK) | (c & ~(AC_MASK | TC_MASK)))))

      if(!tryTerminate(false, false) && w != null && w.array != null) {
        w.cancelAll()
        c = ctl
        var ws: Array[WorkQueue] = workQueues
        var p: Thread = _
        var u: Int = c >>> 32 toInt
        var e: Int = c toInt
        var i: Int = e & SMASK
        var v: WorkQueue = ws(i)

        while(u < 0 && e >= 0) {
          if(e > 0) { // activate or create replacement
            if(ws == null || i >= ws.length || v == null)
              break
            val nc: Long = (((v.nextWait & E_MASK)).toLong | (((u + UAC_UNIT) << 32).toLong))
            if(v.eventCount != (e | INT_SIGN))
              break
            if(U.compareAndSwapLong(this, CTL, c, nc)) {
              v.eventCount = (e + E_SEQ) & E_MASK
              p = v.parker
              if(p != null)
                U.unpark(p)
              break
            }
          } else {
            if(u.toShort < 0)
              tryAddWorker()
            break
          }

          u = (c >>> 32).toInt
          e = c.toInt
          i = e & SMASK
          v = ws(i)
          ws = workQueues
        }
      }
      if(ex == null) // help clean refs on way out
        ForkJoinTask.helpExpungeStaleExceptions()
      else // rethrow
        ForkJoinTask.rethrow(ex)
    }

    // Submissions

    def externalPush(task: ForkJoinTask[_]): Unit = {
      var ws: Array[WorkQueue] = workQueues
      var z: Submitter = submitters.get
      var m: Int = ws.length - 1
      var q: WorkQueue = ws(m & z.seed & SQMASK)
      var a: Array[ForkJoinTask[_]] = q.array
      if (z != null && plock > 0 && ws != null && m >= 0 && q != null && U.compareAndSwapInt(q, QLOCK, 0, 1)) { // lock
        val b: Int = q.base
        val s: Int = q.top
        var n: Int = s + 1 - b
        var an: Int = a.length
        if (a != null && an > n) {
          val j = (((an - 1) & s) << ASHIFT) + ABASE
          U.putOrderedObject(a, j, task)
          q.top = s + 1 // push on to deque

          q.qlock = 0
          if (n <= 2) signalWork(q)
          return
        }
        q.qlock = 0
      }
      fullExternalPush(task)
    }

    private def fullExternalPush(task: ForkJoinTask[_]): Unit = {
      var r: Int = 0 // random index seed
      var z: Submitter = submitters.get()
      while(true) {
        var ws: Array[WorkQueue] = workQueues
        var q: WorkQueue = _
        var ps: Int = plock
        var m: Int = _
        var k: Int = _
        if(z == null) {
          val r1 = INDEXSEED
          r = r1 + SEED_INCREMENT
          if(U.compareAndSwapInt(this, INDEXSEED, r1, r) && r != 0) {
            z = new Submitter(r)
            submitters.set(z)
          }
        } else if(r == 0) {
          r = z.seed
          r ^= r << 13
          r ^= r >>> 17
          z.seed = r ^ (r << 5)
        } else if(ps < 0)
          throw new RejectedExecutionException()
        else if(ps == 0 || ws == null || m < 0) {
          val p: Int = config & SMASK // find power of two table size
          var n: Int = if(p > 1) p -1 else 1 // ensure at least 2 slots
          n |= n >>> 1
          n |= n >>> 2
          n |= n >>> 4
          n |= n >>> 8
          n |= n >>> 16
          n = (n + 1) << 1
          ws = workQueues
          val nws: Array[WorkQueue] = if(ws == null || ws.length == 0) new Array[WorkQueue](n) else null
          val ps1 = ps
          ps += PL_LOCK
          if(ps1 & PL_LOCK != 0 || !U.compareAndSwapInt(this, ps1, ps))
            ps = acquirePlock
          ws = workQueues
          if((ws == null || ws.length == 0) && nws != null)
            workQueues = nws
          val nps: Int = (ps & SHUTDOWN) | (ps + PL_LOCK) & ~SHUTDOWN
          if(!U.compareAndSwapInt(this, PLOCK, ps, nps))
            releasePlock(nps)
        } else {
          k = r & m & SQMASK
          q = ws(k)
          if(q != null) {
            if(q.qlock == 0 && U.compareAndSwapInt(q, QLOCK, 0, 1)) {
              var a: Array[ForkJoinTask[_]] = q.array
              val s: Int = q.top
              var submitted: Boolean = false
              try { // locked version of push
                if((a != null && a.length > s + 1 - q.base) ||
                (q.growArray != null)) { // must presize
                  a = q.growArray
                  val j: Int = (((a.length - 1) & s) << ASHIFT) + ABASE
                  U.putOrderedObject(a, j, task)
                  q.top = s + 1
                  submitted = true
                }
              } finally {
                q.qlock = 0 // unlock
              }
              if(submitted) {
                signalWork(q)
                return
              }
            }
            r = 0 // move on failure
          } else {
            ps = plock
            if(ps & PL_LOCK == 0) { //create new queue
              q = new WorkQueue(this, null, SHARED_QUEUE, r)
              val ps1 = plock
              ps = ps1 + PL_LOCK
              if((ps & PL_LOCK != 0) || !U.compareAndSwapInt(this, PLOCK, ps1, ps))
                ps = acquirePlock
              ws = workQueues
              if(ws != null && k < ws.length && ws(k) == null)
                ws(k) = q
              val nps: Int = (ps & SHUTDOWN) | ((ps + PL_LOCK) & ~SHUTDOWN)
              if(!U.compareAndSwapInt(this, PLOCK, ps, nps))
                releasePlock(nps)
            }
            else r = 0 // try elsewhere while lock held
          }
        }
        z = submitters.get()
      }
    }

    // Maintaining ctl counts

    def incrementActiveCount(): Unit = {
      var c: Long = ctl
      while(!U.compareAndwapLong(this, CTL, c, c + AC_UNIT)) {
        c = ctl
      }
    }

    def signalWork(q: WorkQueue): Unit = {
      val hint: Int = q.poolIndex
      var c: Long = ctl
      var e: Int = _
      var u: Int = (c >>> 32).toInt
      var i: Int = _
      var n: Int = _
      var ws: Array[WorkQueue] = _
      val w: WorkQueue = _
      val p: Thread = _

      while(u < 0) {
        e = c.toInt
        if(e > 0) {
          ws = workQueues
          i = e & SMASK
          w = ws(i)
          if(ws != null && ws.length > i && w != null && w.eventCount == (e | INT_SIGN)) {
            val nc: Long = ((w.nextWait & E_MASK).toLong) | ((u + UAC_UNIT) << 32).toLong
            if(U.compareAndSwapLong(this, CTL, c, nc)) {
              w.hint = hint
              w.eventCount = (e + E_SEQ) & E_MASK
              p = w.parker
              if(p != null)
                U.unpark(p)
              break
            }
            if(q.top - q.base <= 0)
              break
          } else break
        } else {
          if(u.toShort < 0)
            tryAddWorker()
          break
        }
        c = ctl
        u = (c >>> 32).toInt
      }
    }

    // Scanning for tasks

    def runWorker(w: WorkQueue): Unit = {
      w.growArray // allocate queue
      do { w.runTask(scan(w)) } while(w.qlock >= 0)
    }

    // TODO line 2019 scan

  }



  object WorkQueue {

    final val INITIAL_QUEUE_CAPACITY: Int = 1 << 13
    final val MAXIMUM_QUEUE_CAPACITY: Int = 1 << 26 // 64M

    final var defaultForkJoinWorkerThreadFactory: ForkJoinWorkerThreadFactory = _

    final var submitters: ThreadLocal[Submitter] = _

    private final var modifyThreadPermission: RuntimePermission = _

    final var common: ForkJoinPool = _

    final var commonParallelism: Int = _

    private var poolNumberSequence: Int = _

    // synchronized
    private final def nextPoolId: Int = {
      poolNumberSequence += 1
      poolNumberSequence
    }

    // static constants

    private final val IDLE_TIMEOUT: Long = 2000L * 1000L * 1000L // 2sec

    private final val FAST_IDLE_TIMEOUT: Long = 200L * 1000L * 1000L

    private final val TIMEOUT_SLOP: Long = 2000000L

    private final val MAX_HELP: Int = 64

    private final val SEED_INCREMENT: Int = 0x61c88647

    // bit positions/shifts for fields
    private final val AC_SHIFT = 48
    private final val TC_SHIFT = 32
    private final val ST_SHIFT = 31
    private final val EC_SHIFT = 16

    // bounds
    private final val SMASK = 0xffff // short bits

    private final val MAX_CAP = 0x7fff // max #workers - 1

    private final val EVENMASK = 0xfffe // even short bits

    private final val SQMASK = 0x007e // max 64 (even) slots

    private final val SHORT_SIGN = 1 << 15
    private final val INT_SIGN = 1 << 31

    // masks
    private final val STOP_BIT = 0x0001L << ST_SHIFT
    private final val AC_MASK = SMASK.toLong << AC_SHIFT
    private final val TC_MASK = SMASK.toLong << TC_SHIFT

    // units for incrementing and decrementing
    private final val TC_UNIT = 1L << TC_SHIFT
    private final val AC_UNIT = 1L << AC_SHIFT

    // masks and units for dealing with u = (int)(ctl >>> 32)
    private final val UAC_SHIFT = AC_SHIFT - 32
    private final val UTC_SHIFT = TC_SHIFT - 32
    private final val UAC_MASK = SMASK << UAC_SHIFT
    private final val UTC_MASK = SMASK << UTC_SHIFT
    private final val UAC_UNIT = 1 << UAC_SHIFT
    private final val UTC_UNIT = 1 << UTC_SHIFT

    // masks and units for dealing with e = (int)ctl
    private final val E_MASK = 0x7fffffff // no STOP_BIT

    private final val E_SEQ = 1 << EC_SHIFT

    // plock bits
    private final val SHUTDOWN = 1 << 31
    private final val PL_LOCK = 2
    private final val PL_SIGNAL = 1
    private final val PL_SPINS = 1 << 8

    // access mode for WorkQueue
    val LIFO_QUEUE = 0
    val FIFO_QUEUE = 1
    val SHARED_QUEUE: Int = -1

    // bounds for #steps in scan loop -- must be power 2 minus 1
    private final val MIN_SCAN = 0x1ff // cover estimation slop

    private final val MAX_SCAN = 0x1ffff // 4 * max workers

    //volatile
    var pad00, pad01, pad02, pad03, pad04, pad05, pad06: Long = _

    /*volatile*/
    val stealCount = 0L // collects worker counts
    val ctl = 0L // main pool control
    val plock = 0 // shutdown status and seqLock
    val indexSeed = 0 // worker/submitter index seed
    /*no longer volatile*/
    val config = 0 // mode and parallelism level
    val workQueues: Array[WorkQueue] = null // main registry
    final val factory: ForkJoinWorkerThreadFactory = null
    final val ueh: Thread.UncaughtExceptionHandler = null // per-worker UEH
    final val workerNamePrefix: String = null // to create worker name string

  }

}
