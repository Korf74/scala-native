package java.util
package concurrent.locks

// ported from Harmony

import java.util

abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer
  with java.io.Serializable {

  import AbstractQueuedSynchronizer._

  //volatile
  private var head: Node = _

  //volatile
  private val tail: Node = _

  // atomic
  private var state: Int = _

  protected final def getState(): Int = state

  protected final def setState(newState: Int): Unit = state = newState

  // TODO
  protected final def compareAndSetState(expect: Int, update: Int): Boolean = ???

  private def enq(node: Node): Node = {
    while(true) {
      val t: Node = tail
      if(t == null) {
        if(compareAndSetHead(new Node()))
          tail = head
      } else {
        node.prev = t
        if(compareAndSetTail(t, node)) {
          t.next = node
          t
        }
      }
    }
  }

  private def addWaiter(node: Node): Node = {
    val node: Node = new Node(Thread.currentThread(), mode)

    val pred: Node = tail
    if(pred != null) {
      node.prev = pred
      if(compareAndSetTail(pred, node)) {
        pred.next = node
        node
      }
    }
    enq(node)
    node
  }

  private def setHead(node: Node): Unit = {
    head = node
    node.thread = null
    node.prev = null
  }

  private def unparkSuccessor(node: Node): Unit = {
    val ws: Int = node.waitStatus
    if(ws < 0)
      compareAndSetWaitStatus(node, ws, 0)

    val s: Node = node.next
    if(s == null || s.waitStatus > 0) {
      s = null
      var t: Node = tail
      while(t != null && t != node) {
        if(t.waitStatus <= 0)
          s = t
        t = t.prev
      }
    }
    if(s != null)
      LockSupport.unpark(s.thread)
  }

  private def doRealeaseShared(): Unit = {
    while(true) {
      val h: Node = head
      if(h != null && h != tail) {
        val ws: Int = h.waitStatus
        if(ws == Node.SIGNAL) {
          if(!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
            continue
          unparkSuccessor(h)
        }
        else if(ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
          continue
      }
      if(h == head)
        break
    }
  }

  private def setHeadAndPropagate(node: Node, propagate: Int): Unit = {
    val h: Node = head
    setHead(node)

    if(propagate > 0 || h == null || h.waitStatus < 0) {
      val s: Node = node.next
      if(s == null || s.isShared)
        doRealeaseShared()
    }
  }

  private def cancelAcquire(node: Node): Unit = {
    if(node == null)
      return
    node.thread = null

    val pred: Node = node.prev
    while(pred.waitStatus > 0)
      node.prev = pred = pred.prev

    val predNext: Node = pred.next

    node.waitStatus = Node.CANCELLED

    if(node == tail && compareAndSetTail(node, pred)) {
      compareAndSetNext(pred, predNext, null)
    } else {
      var ws: Int = 0
      if(pred != head &&
        ((ws = pred.waitStatus) == Node.SIGNAL ||
          (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
        pred.thread != null) {
        val next: Node = node.next
        if(next != null && next.waitStatus <= 0)
          compareAndSetNext(pred, prednext, next)
      } else {
        unparkSuccessor(node)
      }

      node.next = node
    }
  }

  private def parkAndCheckInterrupt(): Boolean = {
    LockSupport.park()
    Thread.interrupted()
  }

  def acquireQueued(node: Node, arg: Int): Boolean = {
    val failed = true
    try {
      var interrupted = false
      while(true) {
        final val p: Node = node.predecessor()
        if(p == head && tryAcquire(arg)) {
          sethead(node)
          p.next = null
          failed = false
          interrupted
        }
        if(shouldParkAfterFailedAcquire(p, node) &&
          parkAndCheckInterrupt())
          interrupted = true
      }
    } finally {
      if(failed)
        cancelAcquire(node)
    }
  }

  private def doAcquireNanos(arg: Int, nanosTimeout: Long): Boolean = {
    val lastTime: Long = System.nanoTime()
    final val node: Node = addWaiter(Node.EXCLUSIVE)
    var failed = true
    try {
      while(true) {
        final val p: Node = node.predecessor()
        if(p == head && tryAcquire(arg)) {
          setHead(node)
          p.next = null
          failed = false
          true
        }
        if(nanosTimeout <= 0)
          return false
        if(shouldParkAfterFailedAcquire(p, node) &&
          nanosTimeout > spinForTimeoutThreshold)
          LockSupport.parkNanos(nanosTimeout)
        val now: Long = System.nanoTime()
        nanosTimeout -= now - lastTime
        lastTime = now
        if(Thread.interrupted())
          throw new InterruptedException()
      }
    } finally {
      if(failed)
        cancelAcquire(node)
    }
  }

  private def doAcquireShared(arg: Int): Unit = {
    final val node: Node = addWaiter(Node.SHARED)
    var failed = true
    try {
      var interrupted = true
      while(true) {
        final val p: Node = node.predecessor()
        if(p == head) {
          val r: Int = tryAcquireShared(arg)
          if(r >= 0) {
            setHeadAndPropagate(node, r)
            p.next = null
            if(interrupted)
              selfInterrupt()
            failed = false
            return
          }
        }
        if(shouldParkAfterFailedAcquire(p, node) &&
          parkAndCheckInterrupt())
          interrupted = true
      }
    } finally {
      if(failed)
        cancelAcquire(node)
    }
  }

  private def doAcquireSharedInterruptibly(arg: Int) = {
    final val node: Node = addWaiter(Node.SHARED)
    var failed = true
    try {
      while(true) {
        final val p: Node = tryAcquireShared(arg)
        if(r >= 0) {
          setHeadAndPropagate(node, r)
          p.next = null
          failed = false
          return
        }
      }
      if(shouldParkAfterFailedAcquire(p, node) &&
        parkAndCheckInterrupt())
        throw new InterruptedException()
    } finally {
      if(failed)
        cancelAcquire(node)
    }
  }

  private def doAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean = {
    var lastTime: Long = System.nanoTime()
    final val node: Node = addWaiter(Node.SHARED)
    var failed = true
    try {
      while(true) {
        final val p: Node = node.predecessor()
        if(p == head) {
          val r: Int = tryAcquireShared(arg)
          if(r >= 0) {
            setHeadAndPropagate(node, r)
            p.next = null
            failed = false
            true
          }
        }
        if(nanosTimeout <= 0)
          false
        if(shouldParkAfterFailedAcquire(p, node) &&
          nanosTimeout > spinForTimeoutThreshold)
          LockSupport.parkNanos(nanosTimeout)
        val now: Long = System.nanoTime()
        nanosTimeout -= now - lastTime
        lastTime = now
        if(Thread.interrupted())
          throw new InterruptedException()
      }
    } finally {
      if(failed)
        cancelAcquire(node)
    }
  }

  // TODO
  protected def tryAcquire(arg: Int): Boolean = throw new UnsupportedOperationException()

  // TODO
  protected def tryRelease(arg: Int): Boolean = throw new UnsupportedOperationException()

  protected def tryAcquireShared(arg: Int): Int = throw new UnsupportedOperationException()

  protected def tryReleaseShared(arg: Int): Boolean = throw new UnsupportedOperationException()

  protected def isHeldExclusively(): Boolean = throw new UnsupportedOperationException()

  final def acquire(arg: Int): Unit = {
    if(!tryAcquire(arg) &&
      acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
      selfInterrupt()
  }

  final def acquireInterruptibly(arg: Int): Unit = {
    if(Thread.interrupted())
      throw new InterruptedException()
    if(!tryAcquire(arg))
      doAcquireInterruptibly(arg)
  }

  final def tryAcquireNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if(Thread.interrupted())
      throw new InterruptedException()
    tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout)
  }

  final def release(arg: Int): Boolean = {
    if(tryRelease(arg)) {
      val h: Node = head
      if(h != null && h.waitStatus != 0)
        unparkSuccessor(h)
      true
    }
    false
  }

  final def acquireShared(arg: Int): Unit = {
    if(tryAcquireShared(arg) < 0)
      doAcquireShared(arg)
  }

  final def acquireSharedInterruptibly(arg: Int): Unit = {
    if(Thread.interrupted())
      throw new InterruptedException()
    if(tryAcquireShared(arg) < 0)
      doAcquireSharedInterruptibly(arg)
  }

  final def tryAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if(Thread.interrupted())
      throw new InterruptedException()
    tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout)
  }

  final def releaseShared(arg: Int): Boolean = {
    if(tryReleaseShared(arg)) {
      doRealeaseShared()
      true
    }
    false
  }

  final def hasQueuedThreads(): Boolean = head != tail

  final def hasContented(): Boolean = head != null

  final def getFirstQueuedThread(): Thread = (head == tail) ? null : fullGetFirstQueuedThread()

  private def fullGetQueuedThread(): Thread = {
    var s: Node = null
    var h: Node = null
    var st: Thread = null

    if(((h = head) != null && (s = h.next) != null &&
        s.prev == head && (st = s.thread) != null) ||
      ((h = head) != null && (s = h.next) != null &&
        s.prev == head && (st = s.thread) != null))
      st

    var t: Node = tail
    var firstThread: Thread = null
    while(t != null && t != head) {
      val tt: Thread = t.thread
      if(tt != null)
        firstThread = tt
      t = t.prev
    }
    firstThread
  }

  final def isQueued(thread: Thread): Boolean = {
    if(thread == null)
      throw new NullPointerException()
    var p: Node = tail
    while(p != null) {
      if(p.thread == thread)
        true
      p = p.prev
    }
    false
  }

  final def apparentlyFirstQueuedIsExclusive(): Boolean = {
    var h: Node = null
    var s: Node = null
    (h = head) != null && (s = h.next) != null &&
      !s.isShared() && s.thread != null
  }

  final def hasQueuedPredecessors(): Boolean = {
    val t: Node = tail
    val h: Node = head
    var s: Node = null
    h != t && ((s = h.next) == null || s.thread != Thread.currentThread())
  }

  final def getQeueueLength(): Int = {
    var n = 0
    var p: Node = tail
    while(p != null) {
      if(p.thread != null)
        n += 1
      p = p.prev
    }
    n
  }

  final def getQueuedThreads(): util.Collection[Thread] = {
    val list: util.ArrayList[Thread] = new util.ArrayList[Thread]()
    var p: Node = tail
    while(p != null) {
      val t: Thread = p.thread
      if(t != null)
        list.add(t)
      p = p.prev
    }
    list
  }

  final def getExclusiveQueuedThreads(): util.Collection[Thread] = {
    val list: util.ArrayList[Thread] = new util.ArrayList[Thread]()
    var p: Node = tail
    while(p != null) {
      if(!p.isShared()) {
        val t: Thread = p.thread
        if (t != null)
          list.add(t)
        p = p.prev
      }
    }
    list
  }

  final def getSharedQueuedThreads(): util.Collection[Thread] = {
    val list: util.ArrayList[Thread] = new util.ArrayList[Thread]()
    var p: Node = tail
    while(p != null) {
      if(p.isShared()) {
        val t: Thread = p.thread
        if (t != null)
          list.add(t)
        p = p.prev
      }
    }
    list
  }

  def toString: String = {
    val s: Int = getState()
    val q: String = hasQueuedThreads() ? "non" : ""
    super.toString + "[State = " + s + ", " + q + "empty queue]"
  }

  final def isOnSyncQueue(node: Node): Boolean = {
    if(node.waitStatus == Node.CONDITION || node.prev == null)
      false
    if(node.next != null)
      true

    findNodeFromTail(node)
  }

  private def findNodeFromTail(node: Node) = {
    var t: Node = tail
    while(true) {
      if(t == node)
        true
      if(t == null)
        false
      t = t.prev
    }
  }

  final def transferForSignal(node: Node): Boolean = {
    if(!compareAndSetWaitStatus(node, Node.CONDITION, 0))
      false

    val p: Node = enq(node)
    val ws: Int = p.waitStatus
    if(ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
      LockSupport.unpark(node.thread)
    true
  }

  final def transferAfterCancelledWait(node: Node): Boolean = {
    if(compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
      enq(node)
      true
    }

    while(!isOnSyncQueue(node))
      Thread.`yield`()
    false
  }

  final def fullyRelease(node: Node): Int = {
    var failed = true
    try {
      val savedState: Int = getState()
      if(release(savedState)) {
        failed = false
        savedState
      } else {
        throw new IllegalMonitorStateException()
      }
    } finally {
      if(failed)
        node.waitStatus = Node.CANCELLED
    }
  }

  final def owns(condition: ConditionObject) = {
    if(condition == null)
      throw new NullPointerException()
    condition.isOwnedBy(this)
  }

  final def hasWaiters(condition: ConditionObject) = {
    if(!owns(condition))
      throw new IllegalArgumentException("Not owner")
    condition.hasWaiters()
  }

  final def getWaitQueueLength(condition: ConditionObject): Int = {
    if(!owns(condition))
      throw new IllegalArgumentException()
    condition.getWaitQueueLength()
  }

  final getWaitingThreads(condition: ConditionObject): util.Collection[Thread] = {
    if(!owns(condition))
      throw new IllegalArgumentException("Not owner")
    condition.getWaitingThreads()
  }

  class ConditionObject extends Condition with Serializable {

    import ConditionObject._

    private var firstWaiter: Node = null
    private var lastWaiter: Node = null

    private def addConditionWaiter(): Node = {
      var t: Node = lastWaiter

      if(t != null && t.waitStatus != Node.CONDITION) {
        unlinkCancelledWaiters()
        t = lastWaiter
      }
      val node: Node = new Node(Thread.currentThread(), Node.CONDITION)
      if(t == null)
        firstWaiter = node
      else
        t.nextWaiter = node
      lastWaiter = node
      node
    }

    private def doSignal(first: Node): Unit = {
      do {
        if((firstWaiter = first.nextWaiter) == null)
          lastWaiter = null
        first.nextWaiter = null
      } while(!transferForSignal(first) && (first = firstWaiter) != null)
    }

    private def doSignalAll(first: Node) = {
      val lastWaiter: Node = firstWaiter = null
      do {
        val next: Node = first.nextWaiter
        first.nextWaiter = null
        transferForSignal(first)
        first = next
      } while(first != null)
    }

    private def unlinkCancelledWaiters(): Unit = {
      var t: Node = firstWaiter
      val trail: Node = null
      while(t != null) {
        val next: Node = t.nextWaiter
        if(t.waitStatus != Node.CONDITION) {
          t.nextWaiter = null
          if(trail == null)
            firstWaiter = next
          else
            trail.nextWaiter = next
          if(next == null)
            lastWaiter = trail
        }
        else
          trail = t
        t = next
      }
    }

    final signal(): Unit = {
      if(!isHeldExclusively())
        throw new IllegalMonitorStateException()
      val first: Node = firstWaiter
      if(first != null)
        doSignal(first)
    }

    final def signalAll(): Unit = {
      if(!isHeldExclusively())
        throw new IllegalMonitorStateException()
      val first: Node = firstWaiter
      if(first != null)
        doSignalAll(first)
    }

    final def awaitUninterruptibly() = {
      val node: Node = addConditionWaiter()
      val savedState: Int = fullyRelease(node)
      var interrupted = false
      while(!isOnSyncQueue(node)) {
        LockSupport.park()
        if(Thread.interrupted())
          interrupted = true
      }
      if(acquireQueued(node, savedState) || interrupted)
        selfInterrupt()
    }

    private def checkInterruptWhileWaiting(node: Node): Int = {
      Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0
    }

    private def reportInterruptAfterWait(interruptMode: Int): Unit = {
      if(interruptMode == THROW_IE)
        throw new InterruptedException()
      else if(interruptMode == REINTERRUPT)
        selfInterrupt()
    }

    final def await(): Unit = {
      if(Thread.interrupted())
        throw new InterruptedException()
      val node: Node = addConditionWaiter()
      val savedState: Int = fullyRelease(node)
      var interruptMode = 0
      while(!isOnSyncQueue(node)) {
        LockSupport.park()
        if((interruptMode = checkInterruptWhileWaiting(node)) != 0)
          break
      }
      if(acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT
      if(node.nextWaiter != null)
        unlinkCancelledWaiters()
      if(interruptMode != 0)
        reportInterruptAfterWait(interruptMode)
    }

    final def awaitNanos(nanosTimeout: Long): Long = {
      if(Thread.interrupted())
        throw new InterruptedException()
      val node: Node = addConditionWaiter()
      val savedState: Int = fullyRelease(node)
      var lastTime: Long = System.nanoTime()
      var interruptMode: Int = 0
      while(!isOnSyncQueue(node)) {
        if(nanosTimeout <= 0L) {
          transferAfterCancelledWait(node)
          break
        }
        LockSupport.parkNanos(nanosTimeout)
        if((interruptMode = checkInterruptWhileWaiting(node)) != 0)
          break

        val now: Long = System.nanoTime()
        nanosTimeout -= now - lastTime
        lastTime = now
      }
      if(acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT
      if(node.nextWaiter != null)
        unlinkCancelledWaiters()
      if(interruptMode != 0)
        reportInterruptAfterWait(interruptMode)
      nanosTimeout - (System.nanoTime() - lastTime)
    }

    final def awaitUntil(deadline: Date): Boolean = {
      if(deadline == null)
        throw new NullPointerException()
      val abstime: Long = deadline.getTime()
      if(Thread.interrupted())
        throw new InterruptedException()
      val node: Node = addConditionWaiter()
      val savedState: Int = fullyRelease(node)
      var timedout = false
      var interruptMode = 0
      while(!isOnSyncQueue(node)) {
        if(System.currentTimeMillis() > abstime) {
          timedout = transferAfterCancelledWait(node)
          break
        }
        LockSupport.parkUntil(abstime)
        if((interruptMode = checkInterruptWhileWaiting(node)) != 0)
          break
      }
      if(acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT
      if(node.nextWaiter != null)
        unlinkCancelledWaiters()
      if(interruptMode != 0)
        reportInterruptAfterWait(interruptMode)
      !timedout
    }

    final def await(time: Long, unit: TimeUnit): Boolean = {
      if(unit == null)
        throw new NullPointerException()
      val nanosTimeout: Long = unit.toNanos(time)
      if(Thread.interrupted())
        throw new InterruptedException()
      val node: Node = addConditionWaiter()
      val savedState: Int = fullyRelease(node)
      var lastTime: Long = System.nanoTime()
      var timedout = false
      var interruptMode: Int = 0
      while(!isOnSyncQueue(node)) {
        if(nanosTimeout <= 0L) {
          timedout = transferAfterCancelledWait(node)
          break
        }
        if(nanosTimeout >= spinForTimeoutThreshold)
          LockSupport.parkNanos(nanosTimeout)
        if((interruptMode = checkInterruptWhileWaiting(node)) != 0)
          break
        val now: Long = System.nanoTime()
        nanosTimeout -= now - lastTime
        lastTime = now
      }
      if(acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT
      if(node.nextWaiter != null)
        unlinkCancelledWaiters()
      if(interruptMode != 0)
        reportInterruptAfterWait(interruptMode)
      !timedout
    }

    final def isOwnedBy(sync: AbstractQueuedSynchronizer): Boolean = sync == AbstractQueuedSynchronizer.this

    protected final def hasWaiters(): Boolean = {
      if(!isHeldExclusively())
        throw new IllegalMonitorStateException()
      var w: Node = firstWaiter
      while(w != null) {
        if(w.waitStatus == Node.CONDITION)
          true
        w = w.nextWaiter
      }
      false
    }

    protected final def getWaitQueueLength(): Int = {
      if(!isHeldExclusively())
        throw new IllegalMonitorStateException()
      var n = 0
      var w: Node = firstWaiter
      while(w != null) {
        if(w.waitStatus == Node.CONDITION)
          n += 1
        w = w.nextWaiter
      }
      n
    }

    protected final def getWaitingThreads(): util.Collection[Thread] = {
      if (!isHeldExclusively())
        throw new IllegalMonitorStateException()
      val list: util.ArrayList[Thread] = new util.ArrayList[Thread]()
      var w: Node = firstWaiter
      while (w != null) {
        if (w.waitStatus == Node.CONDITION) {
          val t: Thread = w.thread
          if (t != null)
            list.add(t)
          w = w.nextWaiter
        }
      }
      list
    }

  }

  object ConditionObject {

    private final val serialVersionUID: Long = 1173984872572414699L

    private final val REINTERRUPT: Int = 1

    private final val THROW_IE: Int = -1

  }

  // TODO
  /**
    * COMPARE AND SWAP PART
    */
}

object AbstractQueuedSynchronizer {

  private final val serialVersionUID: Long = 7373984972572414691L

  final val spinForTimeoutThreshold: Long = 1000L

  private def shouldParkAfterFailedAcquire(pred: Node, node: Node): Boolean = {
    val ws: Int = pred.waitStatus
    if(ws == Node.SIGNAL) true
    if(ws > 0) {
      do {
        node.prev = pred = pred.prev
      } while(pred.waitStatus > 0)
      pred.next = node
    } else {
      compareAndSetWaitStatus(pred, ws, Node.SIGNAL)
    }
    false
  }

  private selfInterrupt(): Unit = Thread.currentThread().interrupt()

  final class Node {

    import Node._

    //volatile
    var waitStatus: Int = _

    //volatile
    var prev: Node = _

    //volatile
    var next: Node = _

    //volatile
    var thread: Thread = _

    var nextWaiter: Node = _

    def this(thread: Thread, mode: Node) = {
      this()
      this.nextWaiter = mode
      this.thread = thread
    }

    def this(thread: Thread, waitStatus: Int) = {
      this()
      this.waitStatus = waitStatus
      this.thread = thread
    }

    def isShared(): Boolean = nextWaiter == SHARED

    def predecessor(): Node = {
      val p = prev
      if(p == null)
        throw new NullPointerException()
      else p
    }

  }

  object Node {

    final val SHARED: Node = new Node()

    final val EXCLUSIVE: Node = _

    final val CANCELLED: Int = 1

    final val SIGNAL: Int = -1

    final val CONDITION: Int = -2

    final val PROPAGATE: Int = -3

  }

}
