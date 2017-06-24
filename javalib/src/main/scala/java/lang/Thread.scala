package java.lang

import java.security.AccessController
import java.util

import security.fortress.SecurityUtils
import vm.VMStack

class Thread extends Runnable {

  import Thread._

  private var interruptedState   = false
  private[this] var name: String = "main" // default name of the main thread

  var group: ThreadGroup = _

  private var contextClassLoader: ClassLoader = _

  private var daemon: scala.Boolean = false

  private var priority: Int = _

  private var stackSize: scala.Long = 0L

  var started: scala.Boolean = false

  var isAlive: scala.Boolean = false

  private var target: Runnable = _

  private var exceptionHandler: UncaughtExceptionHandler = _

  private var threadId: scala.Long = _

  val lock: Object = new Object()

  var localValues: ThreadLocal.Values = _

  var inheritableValues: ThreadLocal.Values = _

  def this(target: Runnable) = this(null, target, THREAD, 0)

  def this(target: Runnable, name: String) = this(null, target, name, 0)

  def this(name: String) = this(null, null, name, 0)

  def this(group: ThreadGroup, target: Runnable) = this(group, target, THREAD, 0)

  def this(group: ThreadGroup, target: Runnable, name: String) = this(group, target, name, 0)

  def this(gp: ThreadGroup, name: String, nativeAddr: scala.Long, stackSize: scala.Long,
           priority: Int, daemon: scala.Boolean) = {
    val contextLoader: ClassLoader = null

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
    this.threadId = getNextThreadId
    this.name = if(name != null) name else THREAD + threadId
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

    SecurityUtils.putContext(this, AccessController.getContext)
    // adding the thread to the thread group should be the last action
    group.add(this)

    val parent: Thread = Thread.currentThread()
    if(parent != null && parent.inheritableValues != null) {
      inheritableValues = new ThreadLocal.Values(parent.inheritableValues)
    }
  }

  def this(group: ThreadGroup, target: Runnable, name: String, stacksize: scala.Long) = {
    val currentThread: Thread = VMThreadManager.currentThread
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

    val oldRef: ThreadWeakRef = ThreadWeakRef.poll
    val newRef: ThreadWeakRef = new ThreadWeakRef(this)

    val oldPointer: scala.Long = if(oldRef == null) 0 else oldRef.getNativeAddr
    val newPointer: scala.Long = VMThreadManager.init(this, newRef, oldPointer)
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

  def getContextClassLoader: ClassLoader = {
    lock.synchronized{
      // First, if the conditions
      //    1) there is a security manager
      //    2) the caller's class loader is not null
      //    3) the caller's class loader is not the same as or an
      //    ancestor of contextClassLoader
      // are satisfied we should perform a security check.
      val securityManager: SecurityManager = System.getSecurityManager
      if(securityManager != null) {
        //the first condition is satisfied
        val callerClassLoader: ClassLoader = VMClassRegistry.getClassLoader(VMStack.getCallerClass(0))
        if(callerClassLoader != null) {
          //the second condition is satisfied
          var classLoader: ClassLoader = contextClassLoader
          while(classLoader != null) {
            if(classLoader == callerClassLoader) {
              //the third condition is not satisfied
              return contextClassLoader
            }
            classLoader = classLoader.getParent
          }
          //the third condition is satisfied
          securityManager.checkPermission(RuntimePermissionCollection.GET_CLASS_LOADER_PERMISSION)
        }
      }
      contextClassLoader
    }
  }

  final def getName: String = name

  final def getPriority: Int = priority

  def getStackTrace: Array[StackTraceElement] = {
    if(currentThread() != this) {
      val securityManager: SecurityManager = System.getSecurityManager
      if(securityManager != null) {
        securityManager.checkPermission(RuntimePermissionCollection.GET_STACK_TRACE_PERMISSION)
      }
    }
    val ste: Array[StackTraceElement] = VMStack.getThreadStackTrace(this)
    if(ste != null) ste else new Array[StackTraceElement](0)
  }

  final def getThreadGroup: ThreadGroup = group

  def getId: scala.Long = threadId

  def interrupt(): Unit = {
    lock.synchronized{
      checkAccess()
      val status: Int = VMThreadManager.interrupt(this)
      if(status != VMThreadManager.TM_ERROR_NONE)
        throw new InternalError("Thread Manager internal error " + status)
    }
  }

  final def isAlive(): scala.Boolean = {
    lock.synchronized{
      this.isAlive
    }
  }

  final def isDaemon: scala.Boolean = daemon

  def isInterrupted: scala.Boolean = VMThreadManager.isInterrupted(this)

  //synchronized
  final def join(): Unit = {
    while(isAlive())
      wait()
  }

  // synchronized
  final def join(ml: scala.Long): Unit = {
    var millis: scala.Long = ml
    if(millis == 0)
      join()
    else {
      val end: scala.Long = System.currentTimeMillis() + millis
      var continue: scala.Boolean = true
      while(isAlive() && continue) {
        wait(millis)
        millis = end - System.currentTimeMillis()
        if(millis <= 0)
          continue = false
      }
    }
  }

  //synchronized
  final def join(ml: scala.Long, n: Int): Unit = {
    var nanos: Int = n
    var millis: scala.Long = ml
    if(millis < 0 || nanos < 0 || nanos > 999999)
      throw new IllegalArgumentException()
    else if(millis == 0 && nanos == 0)
      join()
    else {
      val end: scala.Long = System.nanoTime() + 1000000 * millis + nanos.toLong
      var rest: scala.Long = 0L
      var continue: scala.Boolean = true
      while(isAlive() && continue) {
        wait(millis, nanos)
        rest = end - System.nanoTime()
        if(rest <= 0)
          continue = false
        if(continue) {
          nanos = (rest % 1000000).toInt
          millis = rest / 1000000
        }
      }
    }
  }

  final def resume(): Unit = {
    checkAccess()
    val status: Int = VMThreadManager.resume(this)
    if(status != VMThreadManager.TM_ERROR_NONE)
      throw new InternalError("Thread Manager internal error " + status)
  }

  def run(): Unit = {
    if(target != null)
      target.run()
  }

  def runImpl(): Unit = {
    lock.synchronized{
      isAlive = true
      started = true
      lock.notifyAll()
    }
  }

  def setContextClassLoader(classLoader: ClassLoader): Unit = {
    lock.synchronized{
      val securityManager: SecurityManager = System.getSecurityManager
      if(securityManager != null)
        securityManager.checkPermission(RuntimePermissionCollection.SET_CONTEXT_CLASS_LOADER_PERSMISSION)
      contextClassLoader = classLoader
    }
  }

  final def setDaemon(daemon: scala.Boolean): Unit = {
    lock.synchronized{
      checkAccess()
      if(isAlive())
        throw new IllegalThreadStateException()
      this.daemon = daemon
    }
  }

  final def setName(name: String): Unit = {
    checkAccess()
    // throws NullPointerException if the given name is null
    this.name = name.toString
  }

  final def setPriority(priority: Int): Unit = {
    checkAccess()
    if(priority > MAX_PRIORITY || priority < MIN_PRIORITY)
      throw new IllegalArgumentException("Wrong Thread priority value")
    val threadGroup: ThreadGroup = group
    this.priority = if(priority > threadGroup.maxPriority) threadGroup.maxPriority else priority
    val status: Int = VMThreadManager.setPriority(this, this.priority)
    if(status != VMThreadManager.TM_ERROR_NONE) {
      //throw new InternalError("Thread Manager internal error " + status)
      // TODO Why is this commented ?
    }
  }

  //synchronized
  def start(): Unit = {
    lock.synchronized{
      if(started)
        //this thread was started
        throw new IllegalThreadStateException("This thread was already started!")
      // adding the thread to the thread group
      group.add(this)

      if(VMThreadManager.start(this, stackSize, daemon, priority) != 0)
        throw new OutOfMemoryError("Failed to create new thread")


      // wjw -- why are we *waiting* for a child thread to actually start running?
      // this *guarantees* two context switches
      // nothing in j.l.Thread spec says we have to do this
      // my guess is that this actually masks an underlying race condition that we need to fix.
      var interrupted: scala.Boolean = false
      while(!this.started) {
        try {
          lock.wait()
        } catch {
          case e: InterruptedException => interrupted = true
        }
      }

      if(interrupted) Thread.currentThread().interrupt()

    }
  }

  def detach(uncaughtException: Throwable): Unit = {
    try {
      if(uncaughtException != null)
        getUncaughtExceptionHandler().uncaughtException(this, uncaughtException)
    } finally {
      group.remove(this)
      this.synchronized{
        isAlive = false
        notifyAll()
      }
    }
  }

  // TODO ENUMERATION

  def getState: Thread.State = {
    var dead: scala.Boolean = false
    lock.synchronized{
      if(started && !isAlive()) dead = true
    }
    if(dead) return State.TERMINATED

    val state = VMThreadManager.getState(this)

    if(0 != (state & VMThreadManager.TM_THREAD_STATE_TERMINATED)) State.TERMINATED
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_WAITING_WITH_TIMEOUT)) State.TIMED_WAITING
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_WAITING)
      || 0 != (state & VMThreadManager.TM_THREAD_STATE_PARKED)) State.WAITING
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER)) State.BLOCKED
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_RUNNABLE)) State.RUNNABLE

    //TODO track down all situations where a thread is really in RUNNABLE state
    // but TM_THREAD_STATE_RUNNABLE is not set.  In the meantime, leave the following
    // TM_THREAD_STATE_ALIVE test as it is.
    else if(0 != (state & VMThreadManager.TM_THREAD_STATE_ALIVE)) State.RUNNABLE
    else State.NEW
  }

  @deprecated
  final def stop(): Unit = {
    lock.synchronized{
      if(isAlive())
        stop(new ThreadDeath())
    }
  }

  @deprecated
  final def stop(throwable: Throwable) = {
    val securityManager: SecurityManager = System.getSecurityManager
    if(securityManager != null) {
      securityManager.checkAccess(this)
      if(currentThread() != this || !throwable.isInstanceOf[ThreadDeath])
        securityManager.checkPermission(RuntimePermissionCollection.STOP_THREAD_PERMISSION)
    }
    if(throwable == null)
      throw new NullPointerException("The argument is null!")
    lock.synchronized{
      if(isAlive()) {
        val status: Int = VMThreadManager.stop(this, throwable)
        if(status != VMThreadManager.TM_ERROR_NONE)
          throw new InternalError("Thread Manager internal error " + status)
      }
    }
  }

  @deprecated
  final def suspend(): Unit = {
    checkAccess()

    val status = VMThreadManager.suspend(this)
    if(status != VMThreadManager.TM_ERROR_NONE)
      throw new InternalError("Thread Manager internal error " + status)
  }

  override def toString: String = {
    val threadGroup: ThreadGroup = group
    val s: String = if(threadGroup == null) "" else threadGroup.name
    "Thread[" + name + "," + priority + "," + s + "]"
  }

  def getUncaughtExceptionHandler: UncaughtExceptionHandler = {
    if(exceptionHandler != null) return exceptionHandler
    getThreadGroup
  }

  def setUncaughtExceptionHandler(eh: UncaughtExceptionHandler): Unit = {
    val sm: SecurityManager = System.getSecurityManager
    if(sm != null)
      sm.checkPermission(RuntimePermissionCollection.MODIFY_THREAD_PERMISSION)
    exceptionHandler = eh
  }

  private def checkGCWatermark(): Unit = {
    currentGCWatermarkCount += 1
    if(currentGCWatermarkCount % GC_WATERMARK_MAX_COUNT == 0)
      System.gc()
  }

  def interrupt(): Unit =
    interruptedState = true

  def isInterrupted: scala.Boolean =
    interruptedState

  final def setName(name: String): Unit =
    this.name = name

  final def getName: String =
    this.name

  def getStackTrace: Array[StackTraceElement] = ???

  def getId: scala.Long = 1

  def getUncaughtExceptionHandler: UncaughtExceptionHandler = ???

  def setUncaughtExceptionHandler(handler: UncaughtExceptionHandler): Unit =
    ???

  def setDaemon(on: scala.Boolean): Unit = ???

  trait UncaughtExceptionHandler {
    def uncaughtException(thread: Thread, e: Throwable): Unit
  }
}

object Thread extends Runnable {

  final val MAX_PRIORITY: Int = 10

  final val MIN_PRIORITY: Int = 1

  final val NORM_PRIORITY: Int = 5

  final val STACK_TRACE_INDENT: String = "    "

  private val MainRunnable = new Runnable { def run(): Unit = () }
  private val MainThread   = new Thread(MainRunnable)

  private var defaultExceptionHandler: UncaughtExceptionHandler = _

  private var threadOrdinalNum: scala.Long = 0

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

  def getAllStackTraces(): java.util.Map[Thread, Array[StackTraceElement]] = {
    val securityManager: SecurityManager = System.getSecurityManager
    if(securityManager != null) {
      securityManager.checkPermission(RuntimePermissionCollection.GET_STACK_TRACE_PERMISSION)
      securityManager.checkPermission(RuntimePermissionCollection.MODIFY_THREAD_GROUP_PERMISSION)
    }

    var parent: ThreadGroup = new ThreadGroup(currentThread().getThreadGroup, "Temporary")
    var newParent: ThreadGroup = parent.getParent
    parent.destroy
    while(newParent != null) {
      parent = newParent
      newParent = parent.getParent
    }
    var threadsCount: Int = parent.activeCount() + 1
    var count: Int = 0
    var liveThreads: Array[Thread] = _
    while(true) {
      liveThreads = new Array[Thread](threadsCount)
      count = parent.enumerate(liveThreads)
      if(count == threadsCount) {
        threadsCount *= 2
      } else
        break
    }

    val map: java.util.Map[Thread, Array[StackTraceElement]] = new util.HashMap[Thread, Array[StackTraceElement]](count + 1)
    var i: Int = 0
    while(i < count) {
      val ste: Array[StackTraceElement] = liveThreads(i).getStackTrace
      if(ste.length != 0)
        map.put(liveThreads(i), ste)
      i += 1
    }

    map
  }

  def getDefaultUncaughtExceptionHandler: UncaughtExceptionHandler = defaultExceptionHandler

  def setDefaultUncaughtHandler(eh: UncaughtExceptionHandler): Unit = {
    val sm: SecurityManager = System.getSecurityManager
    if(sm != null)
      sm.checkPermission(RuntimePermissionCollection.SET_DEFAULT_UNCAUGHT_EXCEPTION_HANDLER_PERMISSION)
    defaultExceptionHandler = eh
  }

  //synchronized
  private def getNextThreadId: scala.Long = {
    threadOrdinalNum  += 1
    threadOrdinalNum
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

  trait UncaughtExceptionHandler {

    def uncaughtException(t: Thread, e: Throwable)

  }

}
