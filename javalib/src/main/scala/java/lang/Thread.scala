package java.lang

import java.security.AccessController

class Thread private (runnable: Runnable) extends Runnable {
  if (runnable ne Thread.MainRunnable) ???

  import Thread._

  private var interruptedState   = false
  private[this] var name: String = "main" // default name of the main thread

  var group: ThreadGroup = _

  private var contextClassLoader: ClassLoader = _

  private var daemon: Boolean = false

  private var name: String = _

  private var priority: Int = _

  private val stackSize: Long = 0L

  var started: Boolean = false

  var isAlive: Boolean = false

  private var target: Runnable = _

  private var exceptionHandler: UncaughtExceptionHandler = _

  private var threadId: Long = _

  val lock: Object = new Object()

  var localValues: ThreadLocal.Values = _

  var inheritableValues: ThreadLocal.Values = _

  def this(target: Runnable) = this(null, target, THREAD, 0)

  def this(target: Runnable, name: String) = this(null, target, name, 0)

  def this(name: String) = this(null, null, name, 0)

  def this(group: ThreadGroup, target: Runnable) = this(group, target, THREAD, 0)

  def this(group: ThreadGroup, target: Runnable, name: String) = this(group, target, name, 0)

  def this(gp: ThreadGroup, name: String, nativeAddr: Long, stackSize: Long,
           priority: Int, daemon: Boolean) = {
    contextClassLoader = null

    var group: ThreadGroup = gp

    if(group == null) {
      if(systemThreadGroup == null) {
        // This is main thread
        systemThreadGroup = new ThreadGroup()
        mainThreadGroup = new ThreadGroup(systemThreadGroup, "main")
        group = mainThreadGroup
      } else {
        group = mainThreadGroup
      }
    }

    this.group = group
    this.stackSize = stackSize
    this.priority = priority
    this.daemon = daemon
    this.threadId = getNextThreadId()
    this.name = if(name != null) name else THREDA + threadId
    // Each thread created from JNI has bootstrap class loader as
    // its context class loader. The only exception is the main thread
    // which has system class loader as its context class loader.
    this.contextClassLoader = contextLoader
    this.target = null
    // The thread is actually running
    this.isAlive = true
    this.started = true

    val newRef: ThreadWeakRef = new ThreadWeakRef(this)
    newRef.setNativeAddr(nativeAddr)

    SecurityUtils.putContext(this, AccessController.getContext())
    // adding the thread to the thread group should be the last action
    group.add(this)

    val parent: Thread = currentThread()
    if(parent != null && parent.inheritableValues != null) {
      inheritableValues = new ThreadLocal.Values(parent.inheritableValues)
    }
  }

  def this(group: ThreadGroup, target: Runnable, name: String, stacksize: Long) = {
    val currentThread: Thread = VMTreadManager.currentThread()
    val securityManager: SecurityManager = System.getSecurityManager

    var threadGroup: ThreadGroup = null
    if(group != null) {
      if(securityManager != null)
        securityManager.checkAccess(group)
      threadGroup = group
    } else if(securityManager != null)
      threadGroup = securityManager.getThreadGroup
    if(threadGroup == null)
      threadGroup = currentThread.group

    threadGroup.checkGroup()

    this.group = threadGroup
    this.daemon = currentThread.daemon
    this.contextClassLoader = currentThread.contextClassLoader
    this.target = target
    this.stackSize = stacksize
    this.priority = currentThread.priority
    this.threadId = getNextThreadId()
    // throws NullPointerException if the guven name is null
    this.name = if(name != THREAD) name.toString else THREAD + threadId

    checkGCWatermark()

    val oldRef: ThreadWeakRef = ThreadWeakRef.poll()
    val newRef: ThreadWeakRef = new ThreadWeakRef(this)

    val oldPointer: Long = if(oldRef == null) 0 else oldRef.getNativeAddr()
    val newPointer: Long = VMThreadManager.init(this, newRef, oldPointer)
    if(newPointer == 0)
      throw new OutOfMemoryError("Failed to create new thread")
    newRef.setNativeAddr(newPointer)

    SecurityUtils.putContext(this, AccessController.getContext)
    checkAccess()

    val parent: Thread = currentThread
    if(parent != null && parent.inheritableValues != null)
      inheritableValues = new ThreadLocal.Values(parent.inheritableValues)
  }

  def this(group: ThreadGroup, name: String) = this(group, null, name, 0)

  final def checkAccess(): Unit = {
    val securityManager: SecurityManager = System.getSecurityManager
    if(securityManager != null)
      securityManager.checkAccess(this)
  }

  def countStackFrames: Int = 0 //deprecated

  def destroy(): Unit =
    // this method is not implemented
    throw new NoSuchMethodError()

  def run(): Unit = ()

  def interrupt(): Unit =
    interruptedState = true

  def isInterrupted(): scala.Boolean =
    interruptedState

  final def setName(name: String): Unit =
    this.name = name

  final def getName(): String =
    this.name

  def getStackTrace(): Array[StackTraceElement] = ???

  def getId(): scala.Long = 1

  def getUncaughtExceptionHandler(): UncaughtExceptionHandler = ???

  def setUncaughtExceptionHandler(handler: UncaughtExceptionHandler): Unit =
    ???

  def setDaemon(on: scala.Boolean): Unit = ???

  trait UncaughtExceptionHandler {
    def uncaughtException(thread: Thread, e: Throwable): Unit
  }
}

object Thread {

  final val MAX_PRIORITY: Int = 10

  final val MIN_PRIORITY: Int = 1

  final val NORM_PRIORITY: Int = 5

  final val STACK_TRACE_INDENT: String = "    "

  private val MainRunnable = new Runnable { def run(): Unit = () }
  private val MainThread   = new Thread(MainRunnable)

  private var defaultExceptionHandler: UncaughtExceptionHandler = _

  private val threadOrdinalNum: Long = 0

  private final val THREAD: String = "Thread-"

  var systemThreadGroup: ThreadGroup = _

  var mainThreadGroup: ThreadGroup = _

  private var currentGCWatermarkCount: Int = 0

  private final val GC_WATERMARK_MAX_COUNT: Int = 700

  def activeCount: Int = currentThread().group.activeCount()

  def currentThread(): Thread = VMTHreadManager.currentThread

  def dumpStack: Unit = {
    val stack: Array[StackTraceElement] = new Throwable().getStackTrace
    System.err.println("Stack trace")
    var i: Int = 0
    while(i < stack.length) {
      System.err.println(STACK_TRACE_INDENT + stack(i))
      i += 1
    }
  }

  def enumerate(list: Array[Thread]): Int = currentThread().group.enumerate(list)

  def holdsLock(obj: Object): Boolean = {
    if(obj == null)
      throw new NullPointerException()
    VMThreadManager.holdsLock(obj)
  }

  def interrupted: Boolean = VMThreadManager.isInterrupted

  def sleep(millis: Long) = sleep(millis, 0)

  def sleep(millis: Long, nanos: Int) = {
    if(millis < 0 || nanos < 0 || nanos > 999999)
      throw new IllegalArgumentException("Arguments don't match the expected range!")
    val status: Int = VMTHreadManager.sleep(millis, nanos)
    if(status == VMThreadManager.TM_ERROR_INTERRUPT)
      throw new InterruptedException()
    else if(status != VMThreadManager.TM_ERROR_NONE)
      throw new InternalError("Thread Manager internal error " + status)
  }

  // TODO correct name ?
  def _yield: Unit = {
    val status: Int = VMThreadManager._yield()
    if(status != VMThreadManager.TM_ERROR_NONE)
      throw new InternalError("Thread Manager internal error " + status)
  }

  def interrupted(): scala.Boolean = {
    val ret = currentThread.isInterrupted
    currentThread.interruptedState = false
    ret
  }

  def sleep(millis: scala.Long, nanos: scala.Int): Unit = {
    import scala.scalanative.posix.errno.EINTR
    import scala.scalanative.native._
    import scala.scalanative.posix.unistd

    def checkErrno() =
      if (errno.errno == EINTR) {
        throw new InterruptedException("Sleep was interrupted")
      }

    if (millis < 0) {
      throw new IllegalArgumentException("millis must be >= 0")
    }
    if (nanos < 0 || nanos > 999999) {
      throw new IllegalArgumentException("nanos value out of range")
    }

    val secs  = millis / 1000
    val usecs = (millis % 1000) * 1000 + nanos / 1000
    if (secs > 0 && unistd.sleep(secs.toUInt) != 0) checkErrno()
    if (usecs > 0 && unistd.usleep(usecs.toUInt) != 0) checkErrno()
  }

  def sleep(millis: scala.Long): Unit = sleep(millis, 0)

  trait UncaughtExceptionHandler {
    def uncaughtException(t: Thread, e: Throwable)
  }

}
