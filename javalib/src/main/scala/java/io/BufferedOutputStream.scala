package java.io

/**
 * Created by remi on 08/03/17.
 */
class BufferedOutputStream(out: OutputStream, size: Int)
    extends FilterOutputStream(out)
    with Flushable
    with Closeable
    with AutoCloseable {

  if(size < 0) throw new IllegalArgumentException()

  def this(in: OutputStream) = this(in, 8192)

  /** The internal buffer array where the data is stored. */
  protected[this] var buf = new Array[Byte](size)

  /** The number of valid bytes in the buffer. */
  protected[this] var count = 0

  private[this] var closed = false

  /**
   * Closes this Output stream and releases any system resources associated with the stream.
   */
  override def close(): Unit = {
    if(!closed) {
      flush()
      closed = true
    }
  }

  /**
   * Writes the specified byte to this buffered output stream.
   */
  override def write(b: Int): Unit = {
    ensureOpen()

    if (count >= buf.length)
      growBuf(1)

    buf(count) = b.toByte
    count += 1
  }

  /**
   * Writes len bytes from the specified byte array starting at offset off to this buffered output stream.
   */
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    ensureOpen()

    if (off < 0 || len < 0 || len > b.length - off)
      throw new IndexOutOfBoundsException()

    if (count + len > buf.length)
      growBuf(len)

    System.arraycopy(b, off, buf, count, len)
    count += len
  }

  /**
   * Flushes this buffered output stream. This forces any buffered output bytes to be written out to the underlying output stream.
   */
  override def flush(): Unit = {
    ensureOpen()

    out.write(buf)
    buf = new Array[Byte](size)
  }

  private def growBuf(minIncrement: Int): Unit = {
    val newSize = Math.max(count + minIncrement, buf.length * 2)
    val newBuf  = new Array[Byte](newSize)
    System.arraycopy(buf, 0, newBuf, 0, count)
    buf = newBuf
  }

  private def ensureOpen(): Unit = {
    if (closed)
      throw new IOException("Operation on closed stream")
  }
}
