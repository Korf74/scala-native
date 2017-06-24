package java.lang

import java.util

class ThreadGroup extends Thread.UncaughtExceptionHandler {

  var maxPriority: Int = Thread.MAX_PRIORITY

  var name: String = "system"

  private var daemon: scala.Boolean = false

  private val destroyed: scala.Boolean = false

  private val groups: List[ThreadGroup] = List.empty[ThreadGroup]

  private var parent: ThreadGroup = _

  private val threads: List[Thread] = List.empty[Thread]

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

  def activeCount(): Int = {
    var count: Int                    = 0
    var groupsCopy: List[ThreadGroup] = null
    var threadsCopy: List[Thread]     = null
    lock.synchronized {
      if (destroyed) return 0
      threadsCopy = threads.clone().asInstanceOf[List[Thread]]
      groupsCopy = groups.clone().asInstanceOf[List[ThreadGroup]]
    }

    count += threadsCopy.count(_.isAlive)

    groupsCopy.foldLeft(count)((c, group) => c + group.activeCount())

    count
  }

  def activeGroupCount(): Int = {
    var count: Int                    = 0
    var groupsCopy: List[ThreadGroup] = null
    lock.synchronized {
      if (destroyed) return 0
      count = groups.size
      groupsCopy = groups.clone().asInstanceOf[List[ThreadGroup]]
    }

    groupsCopy.foldLeft(count)((c, group) => c + group.activeGroupCount())

    count
  }

  @deprecated
  def allowThreadSuspension(b: scala.Boolean): scala.Boolean = false

  def checkAccess(): Unit = {
    val securityManager: SecurityManager = System.getSecurityManager
    if (securityManager != null) securityManager.checkAccess(this)
  }

  def destroy(): Unit = {
    checkAccess()
    lock.synchronized {
      if (destroyed)
        throw new IllegalThreadStateException(
          "The thread group " + name + " is already destroyed!")
      nonsecureDestroy()
    }
  }

  def enumerate(list: Array[Thread]): Int = {
    checkAccess()
    enumerate(list, 0, true)
  }

  def enumerate(list: Array[Thread], recurse: scala.Boolean): Int = {
    checkAccess()
    enumerate(list, 0, recurse)
  }

  def enumerate(list: Array[ThreadGroup]): Int = {
    checkAccess()
    enumerate(list, 0, true)
  }

  def enumerate(list: Array[ThreadGroup], recurse: scala.Boolean): Int = {
    checkAccess()
    enumerate(list, 0, recurse)
  }

  def getMaxPriority: Int = maxPriority

  def getName: String = name

  def getParent: ThreadGroup = {
    if (parent != null) parent.checkAccess()
    parent
  }

  def interrupt(): Unit = {
    checkAccess()
    nonsecureInterrupt()
  }

  def isDaemon: scala.Boolean = daemon

  def isDestroyed: scala.Boolean = destroyed

  def list(): Unit = list("")

  def parentOf(group: ThreadGroup): scala.Boolean = {
    var parent: ThreadGroup = group
    while (parent != null) {
      if (this == parent) return true
      parent = parent.getParent
    }
    false
  }

  @deprecated
  def resume(): Unit = {
    checkAccess()
    nonsecureResume()
  }

  def setDaemon(daemon: scala.Boolean): Unit = {
    checkAccess()
    this.daemon = daemon
  }

  def setMaxPriority(priority: Int): Unit = {
    checkAccess()

    /*
     * GMJ : note that this is to match a known bug in the RI
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4708197
     * We agreed to follow bug for now to prevent breaking apps
     */
    if (priority > Thread.MAX_PRIORITY) return
    if (priority < Thread.MIN_PRIORITY) {
      this.maxPriority = Thread.MIN_PRIORITY
      return
    }
    val new_priority: Int = {
      if (parent != null && parent.maxPriority < priority)
        parent.maxPriority
      else
        priority
    }

    nonsecureSetMaxPriority(new_priority)
  }

  @deprecated
  def stop(): Unit = {
    checkAccess()
    nonsecureStop()
  }

  @deprecated
  def suspend(): Unit = {
    checkAccess()
    nonsecureSuspend()
  }

  override def toString: String = {
    getClass.getName + "[name=" + name + ",maxpri=" + maxPriority + "]"
  }

  def uncaughtException(thread: Thread, throwable: Throwable): Unit = {
    if (parent != null) {
      parent.uncaughtException(thread, throwable)
      return
    }
    val defaultHandler: Thread.UncaughtExceptionHandler =
      Thread.getDefaultUncaughtExceptionHandler
    if (defaultHandler != null) {
      defaultHandler.uncaughtException(thread, throwable)
      return
    }
    if (throwable.isInstanceOf[ThreadDeath]) return
    System.err.println("Uncaught exception in " + thread.getName + ":")
    throwable.printStackTrace()
  }

  def add(thread: Thread): Unit = {
    lock.synchronized {
      if (destroyed)
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
      thread :: threads
    }
  }

  def checkGroup(): Unit = {
    lock.synchronized {
      if (destroyed)
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
    }
  }

  def remove(thread: Thread): Unit = {
    lock.synchronized {
      if (destroyed) return
      threads.filter(_ == thread)
    }
  }

  def add(group: ThreadGroup): Unit = {
    lock.synchronized {
      if (destroyed)
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
      group :: groups
    }
  }

  @SuppressWarnings("unused")
  private def getActiveChildren(): Array[Object] = {
    val threadsCopy: util.ArrayList[Thread] =
      new util.ArrayList[Thread](threads.size)
    val groupsCopy: util.ArrayList[ThreadGroup] =
      new util.ArrayList[ThreadGroup](groups.size)

    lock.synchronized {
      if (destroyed)
        return new Array[Object](2)(null, null)
      threads.foreach(threadsCopy.add)
      groups.foreach(groupsCopy.add)
    }

    val activeThreads: util.ArrayList[Thread] =
      new util.ArrayList[Thread](threadsCopy.size())

    // filter out alive threads
    //TODO from here, also ask Denys about java - Scala collections

  }

  private def enumerate(list: Array[Thread],
                        offset: Int,
                        recurse: scala.Boolean): Int = ???

  private def enumerate(list: Array[ThreadGroup],
                        offset: Int,
                        recurse: scala.Boolean): Int = ???

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
