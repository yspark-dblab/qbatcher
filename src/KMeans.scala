import scala.util.Random
import scala.collection.parallel.immutable.ParSeq
import scala.collection.parallel.mutable.ParArray

class ArrayKMeans(val k: Int, val maxIterations: Int = 100, val tolerance: Double = 1e-4) {
  val rand = new scala.util.Random(0)
  var centroids: Array[Array[Double]] = Array.empty

  def normalize(data: Array[Array[Double]]): Array[Array[Double]] = {
    val n = data.head.length
    val means = Array.fill(n)(0.0)
    val stdDevs = Array.fill(n)(0.0)
    data.foreach { row =>
      for (i <- 0 until n) {
        means(i) += row(i)
      }
    }
    for (i <- 0 until n) {
      means(i) /= data.length
    }
    data.foreach { row =>
      for (i <- 0 until n) {
        stdDevs(i) += (row(i) - means(i)) * (row(i) - means(i))
      }
    }
    for (i <- 0 until n) {
      stdDevs(i) = math.sqrt(stdDevs(i) / data.length)
    }
    data.par.map { row =>
      Array.tabulate(n)(i => if (math.abs(stdDevs(i)) > 1e-9) (row(i) - means(i)) / stdDevs(i) else 0.0)
    }.toArray
  }

  def initializeCentroids(data: Array[Array[Double]]): Unit = {
    if (data.forall(_.sameElements(data.head))) {
      centroids = Array.tabulate(k)(i => {
        val perturbation = Array.fill(data.head.length)((i.toDouble / k) * 1e-1)
        data.head.zip(perturbation).map { case (x, p) => x + p }
      })
    } else {
      val randIndex = rand.nextInt(data.length)
      centroids = Array(data(randIndex))
      for (i <- 1 until k) {
        val distances = data.par.map { row =>
          centroids.map { centroid =>
            row.zip(centroid).map { case (a, b) => {
              (a - b) * (a - b) }
            }.sum
          }.min
        }.toArray
        if (math.abs(distances.sum) < 1e-9) {
          val nextIndex = rand.nextInt(data.length)
          centroids :+= data(nextIndex)
        } else {
          val probabilities = distances.map(_ / distances.sum)
          val cumulativeProbabilities = probabilities.scanLeft(0.0)(_ + _).tail
          val target = rand.nextDouble()
          val nextIndex = cumulativeProbabilities.indexWhere(_ >= target)
          centroids :+= data(nextIndex)
        }
      }
    }
  }

  def fit(data: Array[Array[Double]]): Array[Int] = {
    val normalizedData = normalize(data)
    initializeCentroids(normalizedData)

    var startTime = System.nanoTime()
    var iter = 0
    var change = Double.MaxValue
    startTime = System.nanoTime()
    while (iter < maxIterations && change > tolerance) {
      val oldCentroids = centroids.map(_.clone)
      
      // Use a parallel collection but ensure thread-safe updates
      val clusterSums = ParArray.fill(k)(Array.fill(normalizedData.head.length)(0.0))
      val clusterCounts = ParArray.fill(k)(0)

      normalizedData.par.foreach { point =>
        val closestCentroidIndex = centroids.indices.minBy(i => 
          point.zip(centroids(i)).map { case (a, b) => (a - b) * (a - b) }.sum
        )
        
        // Synchronize updates to shared data
        this.synchronized {
          for (j <- clusterSums(closestCentroidIndex).indices) {
            clusterSums(closestCentroidIndex)(j) += point(j)
          }
          clusterCounts(closestCentroidIndex) += 1
        }
      }

      centroids = clusterSums.zip(clusterCounts).map { case (sum, count) => 
        sum.map(_ / count)
      }.toArray

      change = centroids.zip(oldCentroids).map { case (a, b) =>
        a.zip(b).map { case (x, y) => math.abs(x - y) }.max
      }.max

      iter += 1
    }
    normalizedData.par.map { point =>
      centroids.indices.minBy { i =>
        point.zip(centroids(i)).map { case (a, b) => (a - b) * (a - b) }.sum
      }
    }.toArray
  }
}




  
