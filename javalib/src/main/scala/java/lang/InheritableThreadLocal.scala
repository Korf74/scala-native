package java.lang

class InheritableThreadLocal[T] extends ThreadLocal[T] {
  override protected def childValue(parentValue: T): T = parentValue
}
