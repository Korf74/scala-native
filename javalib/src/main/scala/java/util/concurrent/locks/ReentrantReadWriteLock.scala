package java.util.concurrent.locks
import java.util.concurrent.TimeUnit

class ReentrantReadWriteLock extends ReadWriteLock with java.io.Serializable {

  import ReentrantLock._
  import FairSync._
  import NonfairSync._

  private final var readerLock: ReentrantReadWriteLock.ReadLock = new ReadLock(this)

  private final var writerLock: ReentrantReadWriteLock.WriteLock = new WriteLock(this)

  final var sync: Sync = new NonFairSync()

  def writeLock(): ReentrantReadWriteLock.WriteLock = writerLock

  def readLock(): ReentrantReadWriteLock.ReadLock = readerLock

  final def isFair: Boolean = sync.isInstanceOf[FairSync]

  protected def getOwner: Thread = sync.getOwner

  def getReadLockCount: Int = sync.getReadLockCount

  def isWriteLocked: Boolean = sync.isWriteLocked

  def isWriteLockedByCurrentThread: Boolean = sync.isHeldExclusively

  def getWriteHoldCount: Int = sync.getWriteHoldCount

  def getReadHoldCount: Int = sync.getReadHoldCount

  protected def getQueuedWriterThreads: java.util.Collection[Thread] =
    sync.getExclusiveQueuedThreads

  protected def getQueuedReaderThreads: java.util.Collection[Thread] =
    sync.getSharedQueuedThreads

  final def hasQueuedThreads: Boolean = sync.hasQueuedThreads

  final def hasQueuedThread(thread: Thread) = sync.isQueued(thread)

  final def getQueueLength: Int = sync.getQueueLength

  protected def getQueuedThreads: java.util.Collection[Thread] = sync.getQueuedThreads

  def hasWaiters(condition: Condition): Boolean = {
    if(condition == null)
      throw new NullPointerException()
    if(!(condition.isInstanceOf[AbstractQueuedSynchronizer.ConditionObject]))
      throw new IllegalArgumentException("not owner")
    sync.hasWaiters(condition.asInstanceOf[AbstractQueuedSynchronizer])
  }

  def getWaitQueueLength(condition: Condition): Int = {
    if(condition == null)
      throw new NullPointerException()
    if(!(condition.isInstanceOf[AbstractQueuedSynchronizer.ConditionObject]))
      throw new IllegalArgumentException("not owner")
    sync.getWaitQueueLength(condition.asInstanceOf[AbstractQueuedSynchronizer])
  }

  protected def getWaitingThreads(condition: Condition): java.util.Collection[Thread] = {
    if(condition == null)
      throw new NullPointerException()
    if(!(condition.isInstanceOf[AbstractQueuedSynchronizer.ConditionObject]))
      throw new IllegalArgumentException("not owner")
    sync.getWaitingThreads(condition.asInstanceOf[AbstractQueuedSynchronizer])
  }

  override def toString: String = {
    val c = sync.getCount
    val w = Sync.exclusiveCount(c)
    val r = Sync.sharedCount(c)

    super.toString + "[Write locks = " + w + ", Read locks = " + r + "]"
  }


}

object ReentrantReadWriteLock {

  private final val serialVersionUID: Long = -6992448646407690164L

  abstract class Sync extends AbstractQueuedSynchronizer {

    import Sync._

    //transient
    private var readHolds: ThreadLocalHoldCounter = new ThreadLocalHoldCounter()

    //transient
    private var cacheHoldCounter: HoldCounter = _

    //transient
    private var firstReader: Thread = _

    //transient
    private var firstReaderHoldCount: Int = _

    setState(getState)

    abstract def readerShouldBlock(): Boolean

    abstract def writerShouldBlock(): Boolean

    override protected final def tryRelease(releases: Int): Boolean = {
      if(!isHeldExclusively)
        throw new IllegalMonitorStateException()
      val nextc: Int = getState - releases
      val free: Boolean = exclusiveCount(nextc) == 0
      if(free)
        setExclusiveOwnerThread(null)
      setState(nextc)
      free
    }

    override protected final def tryAcquire(acquires: Int): Boolean = {
      val current: Thread = Thread.currentThread()
      val c: Int = getState
      val w: Int = exclusiveCount(c)
      if(c != 0) {
        if(w == 0 || current != getExclusiveOwnerThread)
          return false
        if(w + exclusiveCount(acquires) > MAX_COUNT)
          throw new Error("Maximum lock count exceeded")
        setState(c + acquires)
        true
      }
      if(writerShouldBlock() || !compareAndSetState(c, c + acquires))
        return false
      setExclusiveOwnerThread(current)
      true
    }

    override protected final def tryReleaseShared(unused: Int): Boolean = {
      val current: Thread = Thread.currentThread()
      if(firstReader == current) {
        if(firstReaderHoldCount == 1)
          firstReader = null
        else firstReaderHoldCount -= 1
      } else {
        val rh: HoldCounter = cacheHoldCounter
        if(rh == null || rh.tid != current.getId)
          rh = readHolds.get()
        val count: Int = rh.count
        if(count <= 1) {
          readHolds.remove()
          if(count <= 0)
            throw unmatchedUnlockException()
        }
        rh.count -= 1
      }
      while(true) {
        val c: Int = getState
        val nextc: Int = c - SHARED_UNIT
        if(compareAndSetState(c, nextc))
          return nextc == 0
      }
    }

    private def unmatchedUnlockException(): IllegalMonitorStateException =
      new IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread")

    protected final def tryAcquireShared(unused: Int): Int = {
      val current: Thread = Thread.currentThread()
      val c: Int = getState
      if(exclusiveCount(c) != 0 && getExclusiveOwnerThread != current) return -1
      val r: Int = sharedCount(c)
      if(!readerShouldBlock() && r < MAX_COUNT && compareAndSetState(c, c + SHARED_UNIT)) {
        if(r == 0) {
          firstReader = current
          firstReaderHoldCount = 1
        } else if(firstReader == current) {
          firstReaderHoldCount += 1
        } else {
          var rh: HoldCounter = cacheHoldCounter
          if(rh == null || rh.tid != current.getId)
            cacheHoldCounter = rh = readHolds.get()
          else if(rh.count == 0)
            readHolds.set(rh)
          rh.count += 1
        }
        return 1
      }
      fullTryAcquireShared(current)
    }

    final def fullTryAcquireShared(current: Thrad) = {
      var rh: HoldCounter = null
      while(true) {
        val c: Int = getState
        if(exclusiveCount(c) != 0) {
          if(getExclusiveOwnerThread != current) return -1
        } else if(readerShouldBlock()) {
          if(firstReader == current) {

          } else {
            if(rh == null) {
              rh = cacheHoldCounter
              if(rh == null || rh.tid != current.getId) {
                rh = readHolds.get
                if(rh.count == 0)
                  readHolds.remove()
              }
            }
            if(rh.count == 0) return -1
          }
        }
        if(sharedCount(c) == MAX_COUNT)
          throw new Error("Maximum lock count exceeded")
        if(compareAndSetState(c, c + SHARED_UNIT)) {
          if(sharedCount(c) == 0) {
            firstReader = current
            firstReaderHoldCount = 1
          } else if(firstReader == current) {
            firstReaderHoldCount += 1
          } else {
            if(rh == null)
              rh = cacheHoldCounter
            if(rh == null || rh.tid != current.getId)
              rh = readHolds.get()
            else if(rh.count == 0)
              readHolds.set(rh)
            rh.count += 1
            cacheHoldCounter = rh
          }
          1
        }
      }
    }

    final def tryWriteLock(): Boolean = {
      val current: Thread = Thread.currentThread()
      val c: Int = getState
      if(c != 0) {
        val w = exclusiveCount(c)
        if(w == 0 || current != getExclusiveOwnerThread)
          return false
        if(w == MAX_COUNT)
          throw new Error("Maximum lock count exceeded")
      }
      if(!compareAndSetState(c, c + 1))
        return false
      setExclusiveOwnerThread(current)
      true
    }

    final def tryReadLock(): Boolean = {
      val current: Thread = Thread.currentThread()
      while(true) {
        val c: Int = getState
        if(exclusiveCount(c) != 0 && getExclusiveOwnerThread != current)
          return false
        val r: Int = sharedCount(c)
        if(r == MAX_COUNT)
          throw new Error("Maximum lock count exceeded")
        if(compareAndSetState(c, c + SHARED_UNIT)) {
          if(r == 0) {
            firstReader = current
            firstReaderHoldCount = 1
          } else if(firstReader == current) {
            firstReaderHoldCount += 1
          } else {
            val rh: HoldCounter = cacheHoldCounter
            if(rh == null || rh.tid != current.getId)
              cacheHoldCounter = rh = readHolds.get()
            else if(rh.count == 0)
              readHolds.set(rh)
            rh.count += 1
          }
          true
        }
      }
    }

    override protected final def isHeldExclusively: Boolean = getExclusiveOwnerThread == Thread.currentThread()

    final def newCondition: ConditionObject = new ConditionObject()

    final def getOwner: Thread = if(exclusiveCount(getState) == 0) null else getExclusiveOwnerThread

    final def getReadLockCount: Int = sharedCount(getState)

    final def isWriteLocked: Boolean = sharedCount(getState) != 0

    final def getWriteHoldCount: Int = if(isHeldExclusively) exclusiveCount(getState) else 0

    final def getReadHoldCount: Int = {
      if(getReadLockCount == 0) return 0
      val current: Thread = Thread.currentThread()
      if(firstReader == current) return firstReaderHoldCount

      val rh: HoldCounter = cacheHoldCounter
      if(rh != null && rh.tid == current.getId) return rh.count

      val count: Int = readHolds.get.count
      if(count == 0) readHolds.remove()
      count
    }

    private def readObject(s: java.io.ObjectInputStream): Unit = {
      s.defaultReadObject()
      readHolds = new ThreadLocalHoldCounter()
      setState(0)
    }

    final def getCount: Int = getState


  }

  object Sync {

    private final val serialVersionUID: Long = 6317671515068378041L

    final val SHARED_SHIFT: Int = 16
    final val SHARED_UNIT: Int = 1 << SHARED_SHIFT
    final val MAX_COUNT: Int = (1 << SHARED_SHIFT) - 1
    final val EXCLUSIVE_MASK: Int = (1 << SHARED_SHIFT) - 1

    def sharedCount(c: Int): Int = c >>> SHARED_SHIFT

    def exclusiveCount(c: Int): Int = c & EXCLUSIVE_MASK

    final class HoldCounter {

      val count: Int = 0

      val tid: Long = Thread.currentThread().getId

    }

    final class ThreadLocalHoldCounter extends ThreadLocal[HoldCounter] {

      override def initialValue(): HoldCounter = new HoldCounter()

    }

  }

  final class NonfairSync extends Sync {

    override def writerShouldBlock(): Boolean = false

    override def readerShouldBlock(): Boolean = apparentlyFirstQueuedIsExclusive()

  }

  object NonfairSync {

    private final val serialVersionUID: Long = -8159625535654395037L

  }

  final class FairSync extends Sync {

    override def writerShouldBlock(): Boolean = hasQueuedPredecessors

    override def readerShouldBlock(): Boolean = hasQueuedPredecessors

  }

  object FairSync {

    private final val serialVersionUID: Long = -2274990926593161451L

  }

  class ReadLock extends Lock with java.io.Serializable {

    private final var sync: Sync = _

    protected def this(lock: ReentrantReadWriteLock) =
      sync = lock.sync

    def lock: Unit = sync.acquireShared(1)

    override def lockInterruptibly(): Unit = sync.acquireSharedInterruptibly(1)

    override def tryLock(): Boolean = sync.tryReadLock()

    override def tryLock(timeout: Long, unit: TimeUnit): Boolean =
      sync.tryAcquireSharedNanos(1, unit.toNanos(timeout))

    override def unlock(): Unit = sync.releaseShared(1)

    override def newCondition(): Condition = throw new UnsupportedOperationException()

    override def toString: String = {
      val r = sync.getReadLockCount
      super.toString + "[Read lock = " + r + "]"
    }

  }

  object ReadLock {

    private final val serialVersionUID: Long = -5992448646407690164L

  }

  class WriteLock extends Lock with java.io.Serializable {

    private final var sync: Sync = _

    protected def this(lock: ReentrantReadWriteLock) =
    sync = lock.sync

    def lock: Unit = sync.acquire(1)

    override def lockInterruptibly(): Unit = sync.acquireInterruptibly(1)

    override def tryLock(): Boolean = sync.tryWriteLock()

    override def tryLock(timeout: Long, unit: TimeUnit): Boolean =
      sync.tryAcquireSharedNanos(1, unit.toNanos(timeout))

    override def unlock(): Unit = sync.release(1)

    override def newCondition(): Condition = sync.newCondition

    override def toString: String = {
      val o: Thread = sync.getOwner
      val s: String = if(o == null) "[Unlocked]" else "[Locked by thread " +o.getName + "]"
      super.toString + s
    }

    def isHeldByCurrentThread: Boolean = sync.isHeldExclusively

    def getHoldCount = sync.getWriteHoldCount

  }

  object WriteLock {

    private final val serialVersionUID: Long = -5992448646407690164L

  }


}
