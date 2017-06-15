package java.util.concurrent.locks

class ReentrantReadWriteLock extends ReadWriteLock with java.io.Serializable {

  private final var readerLock: ReentrantReadWriteLock.ReadLock = new ReadLock(this)

  private final var writerLock: ReentrantReadWriteLock.WriteLock = new WriteLock(this)

  final var sync: Sync = new NonFairSync()

  def writeLock(): ReentrantReadWriteLock.WriteLock = writerLock

  def readLock(): ReentrantReadWriteLock.ReadLock = readerLock


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

    // TODO l 523


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


}
