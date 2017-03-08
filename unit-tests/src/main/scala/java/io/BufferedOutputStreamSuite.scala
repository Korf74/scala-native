package java.io

/**
  * Created by remi on 08/03/17.
  */
object BufferedOutputStreamSuite extends tests.Suite {

  test("write to closed Buffer throws IOException") {

    val out = new BufferedOutputStream(new ByteArrayOutputStream())

    out.close()

    assertThrows[IOException](out.write(1))

  }

  test("simple write") {

    val arrayOut = new ByteArrayOutputStream()

    val out = new BufferedOutputStream(arrayOut)

    out.write(0)
    out.write(1)
    out.write(2)

    out.flush()

    val ans = arrayOut.toByteArray.zipWithIndex
    assert(ans.forall{case (i, idx) => i == idx})
  }

  test("write without flush does nothing") {
    val arrayOut = new ByteArrayOutputStream()

    val out = new BufferedOutputStream(arrayOut)

    out.write(0)
    out.write(1)
    out.write(2)

    assert(arrayOut.toByteArray.isEmpty)
  }

  test("simple write Array") {

    val array = List(0, 1, 2).map(_.toByte).toArray[Byte]

    val arrayOut = new ByteArrayOutputStream()

    val out = new BufferedOutputStream(arrayOut)

    out.write(array, 0, 3)

    out.flush()

    val ans = arrayOut.toByteArray.zipWithIndex
    assert(ans.forall{case (i, idx) => i == idx})

  }

  test("write array with bad index or length throw expceptions") {

    val array = List(0, 1, 2).map(_.toByte).toArray[Byte]

    val arrayOut = new ByteArrayOutputStream()

    val out = new BufferedOutputStream(arrayOut)

    assertThrows[IndexOutOfBoundsException]{
      out.write(array, 0, 4)
    }

    assertThrows[IndexOutOfBoundsException]{
      out.write(array, 4, 3)
    }

    assertThrows[IndexOutOfBoundsException]{
      out.write(array, -1, 3)
    }

    assertThrows[IndexOutOfBoundsException]{
      out.write(array, 4, -1)
    }

  }

}
