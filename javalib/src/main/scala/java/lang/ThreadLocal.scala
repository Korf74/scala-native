package java.lang

import java.lang.ref.Reference

import scala.ref.WeakReference

class ThreadLocal[T] {

  import ThreadLocal._

  private var hasValue: Boolean = false
  private var v: T              = _

  private final val reference: Reference[ThreadLocal[T]] = new WeakReference[ThreadLocal[T]](this)

  private final val hash: Int = hashCounter.fetchAdd(0x61c88647 << 1)

  protected def initialValue(): T = null.asInstanceOf[T]

  @SuppressWarnings("unchecked")
  def get(): T = {
    val currentThread: Thread = Thread.currentThread()
    var values: Values = values(currentThread)
    if(values != null) {
      val table: Array[Object] = values.table
      val index: Int = hash & values.mask
      if(this.reference == table[index]) {
        table[index + 1].asInstanceOf[T]
      }
    } else {
      values = initializeValues(currentThread)
    }

    values.getAfterMiss(this)
  }

  def set(o: T): Unit = {
    val currentThread: Thread = Thread.currentThread()
    var values: Values = values(currentThread)
    if(values == null) {
      values = initializeValues(currentThread)
    }

    values.put(this, value)
  }

  def remove(): Unit = {
    val currentThread: Thread = Thread.currentThread()
    var values: Values = values(currentThread)
    if(values != null) {
      values.remove(this)
    }
  }

  def initializeValues(current: Thread): Values = current.localValues = new Values

  def values(current: Thread): Values = current.localValues
}

object ThreadLocal {

  private val hashCounter: CAtomicInt = CAtomicInt()

  class Values {

    import Values._

    initializeTable(INITIAL_SIZE)

    private var table: Array[Object] = _

    private var mask: Int = _

    private var size: Int = 0

    private var tombstones: Int = 0

    private var maximumLoad: Int = _

    private var clean: Int = _

    def this(fromParent: Values) {
      this()
      table = fromParent.table.clone()
      mask = fromParent.mask
      size = fromParent.size
      tombstones = fromParent.tombstones
      maximumLoad = fromParent.maximumLoad
      clean = fromParent.clean
      inheritValues(fromParent)
    }

    @SuppressWarnings("unchecked")
    private def inheritValues(fromParent: Values): Unit = {
      val table: Array[Object] = table
      var i:  Int = table.length
      while(i >= 0) {
        val k: Object = table(i)

        if(k == null || k == TOMBSTONE) {
          continue
        }

        val reference: Reference[InheritableThreadLocal[_]] = k.asInstanceOf[Reference[InheritableThreadLocal[_]]]

        val key: InheritableThreadLocal[_] = reference.get()
        if(key != null) {
          table(i + 1) = key.childValue(fromParent.table(i + 1))
        } else {
          table(i) = TOMBSTONE
          table(i + 1) = null
          fromParent.table(i) = TOMBSTONE
          fromParent.table(i + 1) = null

          tombstones += 1
          fromParent.tombstones += 1

          size -= 1
          fromParent.size -= 1

          i -= 2
        }
      }
    }

    private def initializeTable(capacity: Int): Unit = {
      this.table = new Object[capacity << 1]
      this.mask = table.length - 1
      this.clean = 0
      this.maximumLoad = capacity * 2 / 3
    }

    private def cleanUp(): Unit = {
      if(rehash) return
      if(size == 0) return

      val index: Int = clean
      val table: Array[Object] = table
      var counter = table.length

      while(counter > 0) {
        val k: Object = table(index)

        if(k == TOMBSTONE || k == null) continue

        val reference: Reference[ThreadLocal[_]] = k.asInstanceOf[Reference[ThreadLocal[_]]]
        if(reference.get() == null) {
          table(index) = TOMBSTONE
          table(index + 1) = null
          tombstones -= 1
          size -= 1
        }

        counter >>= 1
        index = next(index)
      }

      clean = index
    }

    private def rehash(): Boolean = {
      if(tombstones + size < maximumLoad) false

      val capacity: Int = table.length >> 1

      var newCapacity: Int = capacity

      if(size > (capacity >> 1)) {
        newCapacity = capacity << 1
      }

      val oldTable: Array[Object] = table
      initializeTable(newCapacity)

      tombstones = 0

      if(size == 0) true

      var i: Int = oldTable.length - 2
      while(i >= 0) {
        val k: Object = oldTable(i)
        if(k == null || k == TOMBSTONE) continue

        val reference: Reference[ThreadLocal[_]] = k.asInstanceOf[Reference[ThreadLocal[_]]]

        val key: ThreadLocal[_] = reference.get()
        if(key != null) add(key, oldTable(i + 1))
        else size -= 1

        i -= 2
      }

      true
    }

    def add(key: ThreadLocal[_], value: Object) = {
      var index: Int = key.hash & mask
      while(true) {
        val k: Object = table(index)
        if(k == null) {
          table(index) = key.reference
          table(index + 1) = value
          return
        }

        index = next(index)
      }
    }

    def put(key: ThreadLocal[_], value: Object) = {
      cleanUp()

      var firstTombstone: Int = -1

      var index: Int = key.hash & mask
      while(true) {
        val k: Object = table(index)

        if(k == key.reference) {
          table(index + 1) = value
          return
        }

        if(k == null) {
          if(firstTombstone == -1) {
            table(index) = key.reference
            table(index + 1) = value
            size += 1
            return
          }

          table(firstTombstone) = key.reference
          table(firstTombstone + 1) = value
          tombstones -= 1
          size += 1
          return
        }

        if(firstTombstone == -1 && k == TOMBSTONE) firstTombstone = index

        index = next(index)
      }
    }

    def getAfterMiss(key: ThreadLocal[_]) = {
      val table: Array[Object] = table
      var index: Int = key.hash && mask

      if(table(index) == null) {
        val value: Object = key.initialValue()

        if(this.table == table && table(index) == null) {
          table(index) = key.reference
          table(index + 1) = value
          size += 1

          cleanUp()
          value
        }

        put(key, value)
        value
      }

      var firstTombstone: Int = -1

      var index = next(index)
      while(true) {
        val reference: Object = table(index)
        if(reference == key.reference) table(index + 1)

        if(reference == null) {
          val value: Object = key.initialValue()

          if(this.table == table) {
            if(firstTombstone > -1 && table(firstTombstone) == TOMBSTONE) {
              table(firstTombstone) = key.reference
              table(firstTombstone + 1) = value
              tombstones -= 1
              size += 1

              value
            }

            if(table(index) == null) {
              table(index) = key.reference
              table(index + 1) = value
              size += 1

              cleanUp()
              value
            }
          }

          put(key, value)
          value
        }

        if(firstTombstone == -1 && reference == TOMBSTONE) firstTombstone = index

        index = next(index)
      }
    }

    def remove(key: ThreadLocal[_]): Unit = {
      cleanUp()

      var index: Int = key.hash & mask
      while(true) {
        val reference: Object = table(index)

        if(reference == key.reference) {
          table(index) = TOMBSTONE
          table(index + 1) = null
          tombstones += 1
          size -= 1
          return
        }

        if(reference == null) return

        index = next(index)
      }
    }

    private def next(index: Int) = (index + 2) & mask

  }

  object Values {

    private final val INITIAL_SIZE: Int = 16

    private final val TOMBSTONE: Object = new Object()

  }

}
