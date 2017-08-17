package java.lang.ref

abstract class Reference[T >: Null <: AnyRef] {

  /* volatile */
  private[this] val referent: CAtomicRef[T] = CAtomicRef[T]()

  var cleared: Boolean = false

  var queue: ReferenceQueue[_ >: T]

  var next: Reference[_]

  def this(referent: T) = {
    this()
    this.referent.store(referent)
  }

  def this(referent: T, q: ReferenceQueue[_ >: T]) = {
    this()
    this.queue = q
    this.referent.store(referent)
  }

  def get(): T = if(!cleared) referent.load() else null

  def clear(): Unit = {
    cleared = true
    referent.free()
  }

  def isEnqueued: Boolean = next != null

  def enqueue(): Boolean = {
    if (next == null && queue != null) {
      queue.enqueue(this)
      queue = null
      return true
    }
    false
  }
}