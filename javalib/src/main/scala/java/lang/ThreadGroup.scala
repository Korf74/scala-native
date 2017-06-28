package java.lang

import java.util

class ThreadGroup extends Thread.UncaughtExceptionHandler {

  import ThreadGroup._

  var maxPriority: Int = Thread.MAX_PRIORITY

  var name: String = "system"

  private var daemon: scala.Boolean = false

  private var destroyed: scala.Boolean = false

  private val groups: util.LinkedList[ThreadGroup] = new util.LinkedList[ThreadGroup]()

  private var parent: ThreadGroup = _

  private val threads: util.LinkedList[Thread] = new util.LinkedList[Thread]()

  def this(name: String) = this(Thread.currentThread.group, name)

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
    var groupsCopy: util.List[ThreadGroup] = null
    var threadsCopy: util.List[Thread]     = null
    lock.synchronized {
      if (destroyed) return 0
      threadsCopy = threads.clone().asInstanceOf[util.List[Thread]]
      groupsCopy = groups.clone().asInstanceOf[util.List[ThreadGroup]]
    }

    count += threadsCopy.asScala.count(_.isAlive)

    groupsCopy.asScala.foldLeft(count)((c, group) => c + group.activeCount())

    count
  }

  def activeGroupCount(): Int = {
    var count: Int                    = 0
    var groupsCopy: util.List[ThreadGroup] = null
    lock.synchronized {
      if (destroyed) return 0
      count = groups.size
      groupsCopy = groups.clone().asInstanceOf[util.List[ThreadGroup]]
    }

    groupsCopy.asScala.foldLeft(count)((c, group) => c + group.activeGroupCount())

    count
  }

  @deprecated
  def allowThreadSuspension(b: scala.Boolean): scala.Boolean = false

  def checkAccess(): Unit = ()

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
      threads.add(thread)
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
      threads.remove(thread)
      thread.group = null
      if(daemon && threads.isEmpty && groups.isEmpty) {
        // destroy this group
        if(parent != null) {
          parent.remove(this)
          destroyed = null
        }
      }
    }
  }

  def add(group: ThreadGroup): Unit = {
    lock.synchronized {
      if (destroyed)
        throw new IllegalThreadStateException(
          "The thread group is already destroyed!")
      groups.add(group)
    }
  }

  @SuppressWarnings("unused")
  private def getActiveChildren: Array[Object] = {
    val threadsCopy: util.ArrayList[Thread] =
      new util.ArrayList[Thread](threads.size)
    val groupsCopy: util.ArrayList[ThreadGroup] =
      new util.ArrayList[ThreadGroup](groups.size)

    lock.synchronized {
      if (destroyed)
        return new Array[Object](2)(null, null)
      for(thread: Thread <- threads) {
        threadsCopy.add(thread)
      }
      for(group: ThreadGroup <- groups) {
        groupsCopy.add(group)
      }
    }

    val activeThreads: util.ArrayList[Thread] =
      new util.ArrayList[Thread](threadsCopy.size())

    // filter out alive threads
    for(thread: Thread <- threadsCopy) {
      if(thread.isAlive)
        activeThreads.add(thread)
    }

    new Array[Object](2)(activeThreads.toArray(), groupsCopy.toArray())

  }

  private def enumerate(list: Array[Thread],
                        of: Int,
                        recurse: scala.Boolean): Int = {
    var offset: Int = of
    if(list.isEmpty) return 0
    var groupsCopy: util.List[ThreadGroup] = null // a copy of subgroups list
    var threadsCopy: util.List[Thread] = null // a copy of threads list
    lock.synchronized{
      if(destroyed)
        return offset
      threadsCopy = threads.clone().asInstanceOf[util.List[Thread]]
      if(recurse)
        groupsCopy = groups.clone().asInstanceOf[util.List[ThreadGroup]]
    }
    for(thread: Object <- threadsCopy) {
      if(thread.asInstanceOf[Thread].isAlive) {
        list(offset) = thread.asInstanceOf[Thread]
        offset += 1
        if(offset == list.length) return offset
      }
      if(recurse) {
        val it: util.Iterator[ThreadGroup] = groupsCopy.iterator()
        while(offset < list.length && it.hasNext)
          it.next().enumerate(list, offset, true)
      }
    }
    offset
  }

  private def enumerate(list: Array[ThreadGroup],
                        of: Int,
                        recurse: scala.Boolean): Int = {
    var offset: Int = of
    if(destroyed)
      return offset
    val firstGroupIdx: Int = offset
    lock.synchronized{
      for(group: Object <- groups) {
        list(offset) = group.asInstanceOf[ThreadGroup]
        offset += 1
        if(offset == list.length)
          return offset
      }
    }
    if(recurse) {
      val lastGroupIdx: Int = offset
      var i: Int = firstGroupIdx
      while(offset < list.length && i < lastGroupIdx) {
        offset = list(i).enumerate(list, offset, true)
        i += 1
      }
    }
    offset
  }

  private def list(pr: String): Unit = {
    var prefix: String = pr
    println(prefix + toString)
    prefix += LISTING_INDENT
    var groupsCopy: util.List[ThreadGroup] = null // a copy of subgroups list
    var threadsCopy: util.List[Thread] = null // a copy of threads list
    lock.synchronized{
      threadsCopy = threads.clone().asInstanceOf[util.List[Thread]]
      groupsCopy = groups.clone().asInstanceOf[util.List[ThreadGroup]]
    }
    for(thread: Object <- threadsCopy) println(prefix + thread.asInstanceOf[Thread])
    for(group: Object <- groupsCopy) group.asInstanceOf[ThreadGroup].list(prefix)
  }

  private def nonsecureDestroy(): Unit = {
    var groupsCopy: util.List[ThreadGroup] = null

    lock.synchronized{
      if(threads.size > 0)
        throw new IllegalThreadStateException("The thread group " + name + "is not empty")
      destroyed = true
      groupsCopy = groups.clone().asInstanceOf[util.List[ThreadGroup]]
    }

    if(parent != null)
      parent.remove(this)

    for(group: Object <- groupsCopy) group.asInstanceOf[ThreadGroup].nonSecureDestroys
  }

  private def nonsecureInterrupt(): Unit = {
    lock.synchronized{
      for(thread: Object <- threads) thread.asInstanceOf[Thread].interrupt()
      for(group: Object <- groups) group.asInstanceOf[ThreadGroup].nonSecureInterrupt
    }
  }

  private def nonsecureResume(): Unit = {
    lock.synchronized{
      for(thread: Object <- threads) thread.asInstanceOf[Thread].resume()
      for(group: Object <- groups) group.asInstanceOf[ThreadGroup].nonSecureResume
    }
  }

  private def nonsecureSetMaxPriority(priority: Int): Unit = {
    lock.synchronized{
      this.maxPriority = priority

      for(group: Object <- groups) group.asInstanceOf[ThreadGroup].nonSecureSetMaxPriority(priority)
    }
  }

  private def nonsecureStop(): Unit = {
    lock.synchronized{
      for(thread: Object <- threads) thread.asInstanceOf[Thread].stop()
      for(group: Object <- groups) group.asInstanceOf[ThreadGroup].nonSecureStop
    }
  }

  private def nonsecureSuspend(): Unit = {
    lock.synchronized{
      for(thread: Object <- threads) thread.asInstanceOf[Thread].suspend()
      for(group: Object <- groups) group.asInstanceOf[ThreadGroup].nonSecureSuspend
    }
  }

  private def remove(group: ThreadGroup): Unit = {
    lock.synchronized{
      groups.remove(group)
      if(daemon && threads.isEmpty && groups.isEmpty) {
        // destroy this group
        if(parent != null) {
          parent.remove(this)
          destroyed = true
        }
      }
    }
  }

}

object ThreadGroup {

  private final val LISTING_INDENT = "    "
  private class ThreadGroupLock {}
  private final val lock: ThreadGroupLock = new ThreadGroupLock

}
