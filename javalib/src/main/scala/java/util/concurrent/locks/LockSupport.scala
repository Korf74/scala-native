package java.util
package concurrent.locks

private class LockSupport {

}

object LockSupport {

  def unpark(thread: Thread): Unit = ???

  def park(): Unit = ???

  def parkNanos(nanos: Long): Unit = ???

  def parkUntil(deadline: Long): Unit = ???

}
