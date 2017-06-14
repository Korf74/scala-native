package java.lang

class ThreadGroup extends Thread.UncaughtExceptionHandler {

  var maxPriority: Int = Thread.MAX_PRIORITY

  var name: String = "system"

  private var daemon: Boolean = false

  private val destroyed: Boolean = false

  private val groups: List[ThreadGroup] = List.empty[ThreadGroup]

  var parent: ThreadGroup = _

  private val thread: List[Thread] = List.empty[Thread]

  def this(name: String) = this(Thread.currentThread().group, name)

  def this(parent: ThreadGroup, name: String) = {
    this()
    if (parent == null) {
      throw new NullPointerException(
        "The parent thread group specified is null!")
    }

    parent.checkAccess()
    this.name = name
    this.parent = parent
    this.daemon = parent.daemon
    this.maxPriority = parent.maxPriority
    parent.add(this)
  }

  def activeCount(): Int = ???

  def activeGroupCount(): Int = ???

  @deprecated
  def allowThreadSuspension(b: Boolean): Boolean = false

  def checkAccess(): Unit = ???

  def destroy(): Unit = ???

  def enumerate(list: Array[Thread]): Int = ???

  def enumerate(list: Array[Thread], recurse: Boolean): Int = ???

  def enumerate(list: Array[ThreadGroup]): Int = ???

  def enumerate(list: Array[ThreadGroup], recurse: Boolean): Int = ???

  def getMaxPriority(): Int = ???

  def getName(): String = ???

  def getParent(): ThreadGroup = ???

  def interrupt(): Unit = ???

  def isDaemon(): Boolean = ???

  def isDestroyed(): Boolean = ???

  def list(): Unit = ???

  def parentOf(g: ThreadGroup): Boolean = ???

  @deprecated
  def resume(): Unit = ???

  def setDaemon(daemon: Boolean): Unit = ???

  def setMaxPriority(pri: Int): Unit = ???

  @deprecated
  def stop(): Unit = ???

  @deprecated
  def suspend(): Unit = ???

  override def toString(): String = ???

  def uncaughtException(t: Thread, e: Throwable): Unit = ???

  def add(thread: Thread): Unit = ???

  def checkGroup(): Unit = ???

  def remove(thread: Thread): Unit = ???

  def add(group: ThreadGroup): Unit = ???

  @SuppressWarnings("unused")
  private def getActiveChildren(): Array[Object] = ???

  private def enumerate(list: Array[Thread],
                        offset: Int,
                        recurse: Boolean): Int = ???

  private def enumerate(list: Array[ThreadGroup],
                        offset: Int,
                        recurse: Boolean): Int = ???

  private def list(prefix: String): Unit = ???

  private def nonsecureDestroy(): Unit = ???

  private def nonsecureInterrupt(): Unit = ???

  private def nonsecureResume(): Unit = ???

  private def nonsecureSetMaxPriority(priority: Int): Unit = ???

  private def nonsecureStop(): Unit = ???

  private def nonsecureSuspend(): Unit = ???

  private def remove(group: ThreadGroup): Unit = ???

}

object ThreadGroup {

  private final val LISTING_INDENT = "    "
  private class ThreadGroupLock {}
  private final val lock: ThreadGroupLock = new ThreadGroupLock

}
