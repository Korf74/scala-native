package java.lang

object ThreadSuite extends tests.Suite {

  test("basic thread") {
    val thread = new Thread(new Runnable {
      override def run(): Unit = println("thread target running")
    })

    thread.run()
    thread.start()
  }

}
