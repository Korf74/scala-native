import scalanative._
import native._

object Test {
  def main(args: Array[String]): Unit = {
    def f(a: Ptr[Byte]) = a

    val b = CFunctionPtr.fromFunction1(f)
  }
}
