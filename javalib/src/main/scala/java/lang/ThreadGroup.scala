package java.lang

class ThreadGroup {

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
    if(parent == null) {
      throw new NullPointerException("The parent thread group specified is null!")
    }

    parent.checkAccess()
    this.name = name
    this.parent = parent
    this.daemon = parent.daemon
    this.maxPriority = parent.maxPriority
    parent.add(this)
  }

  def activeCount(): Int = {

  }

  def activeGroupCount(): Int = ???

  @deprecated
  def allowThreadSuspension(b: Boolean): Boolean = ???

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

}

object ThreadGroup {

  private final val LISTING_INDENT = "    "
  private class ThreadGroupLock {}
  private final val lock: ThreadGroupLock = new ThreadGroupLock

}
