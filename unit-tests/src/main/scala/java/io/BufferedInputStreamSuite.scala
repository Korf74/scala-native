package java.io

/**
 * Created by remi on 08/03/17.
 */
object BufferedInputStreamSuite extends tests.Suite {

  test("simple reads") {

    val inputArray =
      List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).map(_.toByte).toArray[Byte]

    val arrayIn = new ByteArrayInputStream(inputArray, 0, 10)

    val in = new BufferedInputStream(arrayIn)

    assert(in.read() == 0)

    assert(in.read() == 1)

    assert(in.read() == 2)

    val a = new Array[Byte](7)

    in.read(a)

    assert(a.zipWithIndex.forall { case (i, idx) => i == idx })

  }

}
