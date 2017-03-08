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

}
