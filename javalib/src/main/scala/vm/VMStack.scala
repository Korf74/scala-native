package vm

private final class VMStack {

}

object VMStack {

  //natives
  def getCallerClass(depth: Int): Class[_] = ???

  def getClasses(maxSize: Int, considerPrivileged: scala.Boolean): Array[Class] = ???

  def getStackClasses(state: Object): Array[Class] = ???

  def getStackTrace(state: Object): Array[StackTraceElement] = ???

  def getThreadStackTrace(t: Thread): Array[StackTraceElement] = ???

}
