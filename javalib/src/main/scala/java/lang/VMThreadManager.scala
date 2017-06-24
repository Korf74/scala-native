package java.lang

private final class VMThreadManager {

}

object VMThreadManager {

  /**
    * Thread Manager functions error codes
    */
  final val TM_ERROR_NONE: Int = 0
  final val TM_ERROR_INTERRUPT: Int = 52
  final val TM_ERROR_ILLEGAL_STATE = 51
  final val TM_ERROR_EBUSY: Int = 70025

  /**
    * JVMTI constants
    */
  final val TM_THREAD_STATE_ALIVE: Int = 0x0001
  final val TM_THREAD_STATE_TERMINATED: Int = 0x0002
  final val TM_THREAD_STATE_RUNNABLE: Int = 0x0004
  final val TM_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER: Int = 0x0400
  final val TM_THREAD_STATE_WAITING: Int = 0x0080
  final val TM_THREAD_STATE_WAITING_INDEFINITELY: Int = 0x0010
  final val TM_THREAD_STATE_WAITING_WITH_TIMEOUT: Int = 0x0020
  final val TM_THREAD_STATE_SLEEPING: Int = 0x0040
  final val TM_THREAD_STATE_IN_OBJECT_WAIT: Int = 0x0100
  final val TM_THREAD_STATE_PARKED: Int = 0x0200
  final val TM_THREAD_STATE_SUSPENDED: Int = 0x100000
  final val TM_THREAD_STATE_INTERRUPTED: Int = 0x200000
  final val TM_THREAD_STATE_IN_NATIVE: Int = 0x400000
  final val TM_THREAD_STATE_VENDOR_1: Int = 0x10000000
  final val TM_THREAD_STATE_VENDOR_2: Int = 0x20000000
  final val TM_THREAD_STATE_VENDOR_3: Int = 0x40000000

  //all native from here but currentThread
  def currentThreadNative(): Thread = ???

  def currentThread: Thread = {
    if(VMHelper.isVMMagicPackageSupported())
      return ThreadHelper.getCurrentThread
    currentThreadNative()
  }

  def holdsLock(obj: Object): scala.Boolean = ???

  def interrupt(thread: Thread): Int = ???

  def isAlive(thread: Thread): scala.Boolean = ???

  def isInterrupted: scala.Boolean = ???

  def isInterrupted(thread: Thread): scala.Boolean = ???

  def notify(obj: Object): Int = ???

  def notifyAll(obj: Object): Int = ???

  def resume(thread: Thread): Int = ???

  def setPriority(thread: Thread, priority: Int): Int = ???

  def sleep(millis: scala.Long, nanos: scala.Int): Int = ???

  def start(thrad: Thread, stackSize: scala.Long, daemon: scala.Boolean, priority: Int) = ???

  def stop(thread: Thread, throwable: Throwable): Int = ???

  def suspend(thread: Thread): Int = ???

  def wait(obj: Object, millis: scala.Long, nanos: Int): Int = ???

  def _yield(): Int = ???

  def init(thread: Thread, ref: ThreadWeakRef, oldAddr: scala.Long): scala.Long = ???

  def getState(thread: Thread): Int = ???

}
