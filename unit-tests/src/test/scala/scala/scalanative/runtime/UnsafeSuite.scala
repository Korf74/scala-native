package scala.scalanative.runtime

object UnsafeSuite extends tests.Suite {

  test("Compare and Swap Objects") {

    val obj1 = "hello"
    val obj2 = "world!"

    assert(Unsafe.compareAndSwapObject(obj1, obj1, obj2))
    println(obj1)
    println(obj2)
    //assert(obj1 == "world!")

  }

  test("Thread park/unpark") {

  }

}
