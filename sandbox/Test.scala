import scalanative._
import native._

object Test {
  def main(args: Array[String]): Unit = {

    case class obj(var x, var y)

    val objs = (0 until 100).map { i =>
      new obj(i, i)
    }.toSet

    class Cluster(var l: List[obj], var mean: (Int, Int)) {

      private def computeMean(): Unit = {}

      def mean: (Int, Int) = {
        computeMean()
        mean
      }

    }

    def kmeans(l: Set[obj], clusters: List[]): List[(Int, Int)] = {

      def iterate(means: List[(Int, Int)]): Unit = {

      }

      def computeMeans(l: List[obj], means: List[(Int, Int)]): List[(Int, Int)] = {

      }

    }

  }
}
