package java.util.concurrent
package locks

import java.lang

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
      val ws: Int = 0
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

  private def doAcquireNanos(arg: Int, nanosTimeout: Long) = {
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
