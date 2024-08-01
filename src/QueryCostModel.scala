/**
  * @inputParam dataset ("bra" or "ebay")
  * @inputParam trainingFilePath
  * @inputParam testFilePath
  * @inputParam regressionModelType ("elasticNet", "decisionTree", "randomForest", or "gbt")
  * @inputParam numIter (number of iterations for the regression model)
  * @inputParam numClusters (number of clusters for K-means)
  * @inputParam featureColumns (array of feature columns)
  * @inputParam featureColsForClustering (array of feature columns for K-means)
  * @inputParam labelCol (label column)
  * @inputParam outputFilePath (output file path)
  * @inputParam numQueries (number of queries)
  */

import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.ml.regression.LinearRegressionModel
import org.apache.spark.ml.regression.DecisionTreeRegressor
import org.apache.spark.ml.regression.DecisionTreeRegressionModel
import org.apache.spark.ml.regression.RandomForestRegressor
import org.apache.spark.ml.regression.RandomForestRegressionModel
import org.apache.spark.ml.regression.GBTRegressor
import org.apache.spark.ml.regression.GBTRegressionModel
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.tuning.{ParamGridBuilder, CrossValidator}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.ml.clustering.KMeans
import org.apache.spark.sql.expressions.Window
import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.spark.ml.clustering.BisectingKMeans

import scala.util.Sorting
import scala.collection.mutable.{ArrayBuffer, ArrayBuilder}
import scala.io.Source
import scala.collection.mutable
//import scala.math.{sqrt, log}

import java.io.File
import java.nio.file.{Files, Paths}
import java.io.BufferedWriter
import java.io.FileWriter

class QueryCostModel(
    dataset: String,
    trainingFilePath: String,
    testFilePath: String,
    regressionModelType: String,
    numIter: Int,
    clusteringAlgorithm: String,
    numClusters1: Int,
    numClusters2: Int,
    featureColumns: Array[String],
    featureColsForClustering1: Array[String],
    featureColsForClustering2: Array[String],
    labelCol: String, // "col34"
    outputFilePath: String,
    numQueries: Int,
    trainingFilePathForBatchScheduler: String,
    regressionModelTypeForBatchScheduler: String,
    featureColsForBatchScheduler: Array[String],
    numIterForBatchScheduler: Int,
    numBatchCol: (String, String),
    labelColForBatchScheduler: String
) {
  var optimalClusteredData: DataFrame = null
  var optimalAssignments: Array[Int] = null
  var optimalCost: Double = Double.PositiveInfinity
  var optimalNumClusters: (Int, Int) = null
  var allPredictions: DataFrame = null
  val random = new scala.util.Random(0)  // fixed seed
  val epsilon = 0.1
  var predictionsMap: mutable.Map[Int, mutable.Map[String, Double]] = mutable.Map()
  val MAX_NUM_CLUSTERS = 17
  // delcare 2D array with MAX_NUM_CLUSTERS by MAX_NUM_CLUSTERS (cacheCosts) initized by -1.0
  var cacheCost: Array[Array[Double]] = Array.fill(MAX_NUM_CLUSTERS, MAX_NUM_CLUSTERS)(-1.0)
  val maxIter = 10

  val trainingData = spark.read.option("header", "false").option("inferSchema", "true").csv(s"${trainingFilePath}").repartition(spark.sparkContext.defaultParallelism)
  val testData = spark.read.option("header", "false").option("inferSchema", "true").csv(s"${testFilePath}").repartition(spark.sparkContext.defaultParallelism)

  var gSumOfInputCard = 0.0
  var gSumOfBaseCard = 0.0
  var gSumOfOutputCard = 0.0
  var gSumOfMapCount = 0.0
  var gSumOfFlatMapCount = 0.0
  var gSumOfFilterCount = 0.0
  var gSumOfJoinCount = 0.0
  var gSumOfReduceByKeyCount = 0.0
  var gSumOfShuffleInputCard = 0.0
  var gSumOfShuffleOutputCard = 0.0
  var gAvgRowLengthForInput = 0.0
  var gAvgRowLengthForBase = 0.0
  var gAvgRowLengthForOutput = 0.0
  var gAvgRowLengthForShuffleInput = 0.0
  var gAvgRowLengthForShuffleOutput = 0.0
  var gDistinctNumTemplates = 0.0
  var gSumOfElapsedTime = 0.0
  var gNumQueries = 0.0

  var groupedDataMap: mutable.Map[Int, Map[Int, Array[((Int, Array[Double], Array[Double]), Int)]]] = mutable.Map()
  var sortedClusterIdsMap: mutable.Map[Int, Array[Int]] = mutable.Map()
  var maxK2: mutable.Map[Int, Int] = mutable.Map().withDefaultValue(0)
  var assignmentByK: mutable.Map[(Int, Int), Array[Int]] = mutable.Map()
  var indexByRowId: Map[Int, Int] = Map()

  val USE_HISTORY = false

  var columns = trainingData.columns
  val renamedTrainingData = addDerivedFeatures(columns.zipWithIndex.foldLeft(trainingData) { (tempData, col) =>
    tempData.withColumnRenamed(col._1, s"col${col._2}")
  }.na.drop())

  columns = testData.columns
  val renamedTestData = addDerivedFeatures(columns.zipWithIndex.foldLeft(testData) { (tempData, col) =>
    if (col._2 == 0) {
      tempData.withColumnRenamed(col._1, s"row_id")
    } else {
      tempData.withColumnRenamed(col._1, s"col${col._2 - 1}")
    }
  }.na.drop())
  .filter(col("row_id") <= numQueries)

  val featureCols = featureColumns // renamedTrainingData.columns.dropRight(1)
  val assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features")
  val assembledTrainingData = assembler.transform(renamedTrainingData).cache()
  val assembledTestData = assembler.transform(renamedTestData).cache()

  val workloadCostModel = new WorkloadCostModel(trainingFilePathForBatchScheduler,
    regressionModelTypeForBatchScheduler,
    numIterForBatchScheduler,
    featureColsForBatchScheduler.filter(x => x != "col18" && x != "col19"),
    labelColForBatchScheduler)
  if (numClusters1 < 0 && numClusters2 < 0) {
    workloadCostModel.fit()
    //System.exit(0)
  }

  def addDerivedFeatures(data: DataFrame): DataFrame = {
    data.withColumn("col35", sqrt(col("col0")))
      .withColumn("col36", sqrt(col("col1")))
      .withColumn("col37", col("col1") * col("col2"))
      .withColumn("col38", col("col0") * col("col2"))
      .withColumn("col39", coalesce(col("col1") * log(col("col2")), lit(0)))
      .withColumn("col40", coalesce(col("col0") * log(col("col2")), lit(0)))
      .withColumn("col41", coalesce(log(col("col0")) * log(col("col2")), lit(0)))
      .withColumn("col42", coalesce(log(col("col1")) * log(col("col2")), lit(0)))
      .withColumn("col43", col("col6") + col("col7"))
      .withColumn("col44", col("col10") * col("col0"))
      .withColumn("col45", col("col11") * col("col1"))
      .withColumn("col46", coalesce(col("col0") * log(col("col10")), lit(0)))
      .withColumn("col47", coalesce(col("col1") * log(col("col11")), lit(0)))
      .withColumn("col48", coalesce(col("col2") * log(col("col12")), lit(0)))
  }

  def trainModel(
      assembledTrainingData: DataFrame,
      regressionModelType: String
  ): Any = {
    val regressionModel = regressionModelType match {
      case "elasticNet" =>
        new LinearRegression()
          .setLabelCol(labelCol)
          .setFeaturesCol("features")
          .setMaxIter(numIter)
          .setElasticNetParam(0.5)
          .setRegParam(1.0)
          .setFitIntercept(true)
      case "decisionTree" =>
        new DecisionTreeRegressor()
          .setLabelCol(labelCol)
          .setFeaturesCol("features")
          .setMaxDepth(15)
      case "randomForest" =>
        new RandomForestRegressor()
          .setLabelCol(labelCol)
          .setFeaturesCol("features")
          .setNumTrees(20)
          .setMaxDepth(5)
      case "gbt" =>
        new GBTRegressor()
          .setLabelCol(labelCol)
          .setFeaturesCol("features")
          .setMaxIter(numIter)
          .setMaxDepth(5)
      case _ =>
        throw new IllegalArgumentException(s"Unsupported regression model type: $regressionModelType")
    }
    regressionModel.fit(assembledTrainingData)
  }

  def estimateQueryCost(inputModel: Any): DataFrame = {
    val model = regressionModelType match {
      case "elasticNet" =>
        inputModel.asInstanceOf[LinearRegressionModel]
      case "decisionTree" =>
        inputModel.asInstanceOf[DecisionTreeRegressionModel]
      case "randomForest" =>
        inputModel.asInstanceOf[RandomForestRegressionModel]
      case "gbt" =>
        inputModel.asInstanceOf[GBTRegressionModel]
      case _ =>
        throw new IllegalArgumentException(s"Unsupported regression model type: $regressionModelType")
    }
    val startTime = System.currentTimeMillis
    val fn = s"/root/qbatcher/results/predictions-${dataset}.csv"
    val pred = spark.read.option("header", "false").option("inferSchema", "true").csv(s"${fn}").repartition(spark.sparkContext.defaultParallelism).toDF("row_id", "prediction")
    val predictions = assembledTestData.join(pred, Seq("row_id"))
    predictions
  }

  def getVariance(features: Array[Array[Double]]): Array[Double] = {
    val numFeatures = features.head.length
    val numDataPoints = features.length.toDouble
    
    val sumAndCount = features.par.aggregate(Array.fill(numFeatures)(0.0) -> 0) (
      { case ((sum, count), row) =>
        for (i <- 0 until numFeatures) {
          sum(i) += row(i)
        }
        (sum, count + 1)
      },
      { case ((sum1, count1), (sum2, count2)) =>
        for (i <- 0 until numFeatures) {
          sum1(i) += sum2(i)
        }
        (sum1, count1 + count2)
      }
    )

    val means = sumAndCount._1.map(_ / numDataPoints)
    
    val sumOfSquares = features.par.aggregate(Array.fill(numFeatures)(0.0)) (
      { (sum, row) =>
        for (i <- 0 until numFeatures) {
          val diff = row(i) - means(i)
          sum(i) += diff * diff
        }
        sum
      },
      { (sum1, sum2) =>
        for (i <- 0 until numFeatures) {
          sum1(i) += sum2(i)
        }
        sum1
      }
    )

    val variances = sumOfSquares.map(_ / (numDataPoints - 1))
    variances
  }

  def secondLevelClustering(assignments: Array[Int], targetClusters: Array[Int], groupedData: Map[Int, Array[((Int, Array[Double], Array[Double]), Int)]], numClusters: (Int, Int)): Array[Int] = {

    val features2ByCluster = groupedData.filter { case (clusterId, _) => targetClusters.contains(clusterId) }
      .map { case (clusterId, clusterData) =>
        (clusterId, clusterData.map { case ((rowId, features1, features2), clusterId) => (rowId, features2) })
      }
    var count = 0
    features2ByCluster.foreach { case (clusterId, clusterData) =>
        count += 1
        val maxClusterId = numClusters._1 + clusterId * 2
        val clusteringModel = new ArrayKMeans(2)
        val newAssignments = clusteringModel.fit(clusterData.map(_._2))
        clusterData.zip(newAssignments).foreach { case ((rowId, features2), newClusterId) =>
          assignments(indexByRowId(rowId)) = newClusterId + maxClusterId
        }
        assignmentByK((numClusters._1, maxK2(numClusters._1) + count)) = assignments.clone()
      }

    assignments
  }

  def twoLevelClusteringForArray(data: Array[(Int, Array[Double], Array[Double])], numClusters: (Int, Int)): Array[Int] = {
    if (data.size <= numClusters._1 + numClusters._2) {
      return (0 until data.size).toArray
    }
    if (assignmentByK.contains(numClusters)) {
      return assignmentByK(numClusters)
    }
    var assignments: Array[Int] = if (numClusters._1 == 1) {
      val assn = Array.fill(data.size)(0)
      assignmentByK((numClusters._1, 0)) = assn.clone()
      assn
    } else {
      if (!assignmentByK.contains((numClusters._1, 0))) {
        val clusteringModel = new ArrayKMeans(numClusters._1)
        val assn: Array[Int] = clusteringModel.fit(data.map(_._2))
        assignmentByK((numClusters._1, 0)) = assn.clone()
        assn
      } else {
        assignmentByK((numClusters._1, 0))
      }
    }
    if (numClusters._2 == 0) {
      return assignments
    }
    val groupedData = data.zip(assignments)
        .groupBy { case ((rowId, features1, features2), clusterId) => clusterId }
    val clusterIds = if (!sortedClusterIdsMap.contains(numClusters._1)) {
      val varianceDF = groupedData.map { case (clusterId, clusterData) =>
        val features1 = clusterData.map { case ((rowId, features1, features2), clusterId) => features1 }
        val varianceVal = getVariance(features1)(0)
        (clusterId, varianceVal)
      }.toArray.sortBy(-_._2)
      val curClusterIds = varianceDF.map(_._1).toArray
      sortedClusterIdsMap(numClusters._1) = curClusterIds.clone()
      curClusterIds
    } else {
      sortedClusterIdsMap(numClusters._1)
    }

    val subClusterIds = clusterIds.slice(maxK2(numClusters._1), numClusters._2)

    val newAssn = assignmentByK((numClusters._1, maxK2(numClusters._1))).clone()
    val resultAssignments = secondLevelClustering(newAssn, subClusterIds, groupedData, numClusters)

    if (maxK2(numClusters._1) < numClusters._2) {
      maxK2(numClusters._1) = numClusters._2
    }

    resultAssignments
  }

  def greedyOrder(executionTimes: Seq[(Int, Double)]): Seq[Int] = {
    executionTimes.zipWithIndex.sortBy { case ((n, t), _) => -n.toDouble / t }.map(_._2)
  }

  def computeAvgTime(order: Seq[Int], executionTimes: Seq[(Int, Double)]): Double = {
    var totalTime = 0.0
    var totalQueries = 0
    var accTime = 0.0
    for (idx <- order.indices) {
      val (n, t) = executionTimes(order(idx))
      totalTime += n * (t + accTime)
      accTime += t
      totalQueries += n
    }
    totalTime / totalQueries
  }

  def optimalAvgTime(executionTimes: Seq[(Int, Double)]): Double = {
    if (executionTimes.size > 10) {
      val order = greedyOrder(executionTimes)
      computeAvgTime(order, executionTimes)
    } else {
      val indices = executionTimes.indices.toSeq
      var minAvgTime = Double.PositiveInfinity
      var bestOrder: Seq[Int] = Seq()

      for (order <- indices.permutations) {
        val avgTime = computeAvgTime(order, executionTimes)
        if (avgTime < minAvgTime) {
          minAvgTime = avgTime
          bestOrder = order
        }
      }

      minAvgTime
    }
  }

  def getCost(data: Array[(Int, Array[Double], Array[Double])], numClusters: (Int, Int)): Double = {
    if (cacheCost(numClusters._1)(numClusters._2) > -1e-9) {
      return cacheCost(numClusters._1)(numClusters._2)
    }
    val assignments = twoLevelClusteringForArray(data, numClusters)
    var featuresByCluster = mutable.Map[Int, Array[Double]]()
    var templatesByCluster = mutable.Map[Int, ArrayBuilder[Double]]()

    var startTime = System.currentTimeMillis()
    for (i <- 0 until assignments.size) {
      val rowId = data(i)._1
      val cluster = assignments(i)
      if (!featuresByCluster.contains(cluster)) {
        featuresByCluster(cluster) = Array.fill(featureColsForBatchScheduler.size - 2)(0.0)
        templatesByCluster(cluster) = ArrayBuilder.make[Double]
      }
      for (i <- 0 until 15) {
        featuresByCluster(cluster)(i) = featuresByCluster(cluster)(i) + predictionsMap(rowId)(s"col$i")
      }
      templatesByCluster(cluster) += predictionsMap(rowId)("col15")
      featuresByCluster(cluster)(16) = featuresByCluster(cluster)(16) + predictionsMap(rowId)("prediction")
      featuresByCluster(cluster)(17) = featuresByCluster(cluster)(17) + 1.0
    }
    val executionTimes = featuresByCluster.keys.map { cluster =>
      for (i <- 10 until 15) {
        featuresByCluster(cluster)(i) = featuresByCluster(cluster)(i) / featuresByCluster(cluster)(17)
      }
      featuresByCluster(cluster)(15) = templatesByCluster(cluster).result().distinct.length.toDouble
      (featuresByCluster(cluster)(17).toInt, workloadCostModel.estimateWorkloadCost(
        Seq((featuresByCluster(cluster)(0), featuresByCluster(cluster)(1), featuresByCluster(cluster)(2), featuresByCluster(cluster)(3), featuresByCluster(cluster)(4),
      featuresByCluster(cluster)(5), featuresByCluster(cluster)(6), featuresByCluster(cluster)(7), featuresByCluster(cluster)(8), featuresByCluster(cluster)(9),
      featuresByCluster(cluster)(10), featuresByCluster(cluster)(11), featuresByCluster(cluster)(12), featuresByCluster(cluster)(13), featuresByCluster(cluster)(14), featuresByCluster(cluster)(15), featuresByCluster(cluster)(16), featuresByCluster(cluster)(17)
       ))
       ))
    }.toArray
    startTime = System.currentTimeMillis()
    cacheCost(numClusters._1)(numClusters._2) = optimalAvgTime(executionTimes.toSeq)
    if (optimalCost > cacheCost(numClusters._1)(numClusters._2)) {
      optimalCost = cacheCost(numClusters._1)(numClusters._2)
      optimalNumClusters = numClusters
      optimalAssignments = assignments
    }
    cacheCost(numClusters._1)(numClusters._2)
  }

  def explore(data: Array[(Int, Array[Double], Array[Double])], numClusters: (Int, Int)): Unit = {
    var currentNumClusters = numClusters
    var iter = 0
    var startTime = System.currentTimeMillis()
    val period = 2
    var hasConverged = false
    var prevOptCost = 999999999.0
    val threshold = 1e-9
    while (iter < maxIter && !hasConverged) {
      val currentCost = getCost(data, currentNumClusters)
      
      val candidateCosts = List((currentNumClusters._1 + 1, scala.math.min(currentNumClusters._1 + 1, currentNumClusters._2)), (currentNumClusters._1, scala.math.min(currentNumClusters._1, currentNumClusters._2 + 1)), (currentNumClusters._1 - 1, scala.math.min(currentNumClusters._1 - 1, currentNumClusters._2)), (currentNumClusters._1, scala.math.min(currentNumClusters._1, currentNumClusters._2 - 1)))
        .par.filter { case (k1, k2) => {
          val cond = k1 > 0 && k1 < 17 && k2 >= 0 && k2 < 17 && k1 >= k2 && k1 + k2 < 17 && k1 + k2 > 1 && (!featureColsForClustering1.contains("s0") || k2 == 0)
          cond
        }}
        .map { nextNumClusters => (nextNumClusters, getCost(data, nextNumClusters)) }
        .toArray
      
      startTime = System.currentTimeMillis()
      // choose nextNumClusters using epsilon greedy = 0.1
      val randomValue = random.nextDouble()
      currentNumClusters = if (randomValue < epsilon) {
        if (USE_HISTORY) {
          // if (cacheCost(numClusters._1)(numClusters._2) > -1e-9) {
          val filteredCandidateCosts = candidateCosts.filter(x => cacheCost(x._1._1)(x._1._2) <= 1e-9)
          if (filteredCandidateCosts.size > 0) {
            filteredCandidateCosts(random.nextInt(filteredCandidateCosts.size))._1
          } else {
            hasConverged = true
            currentNumClusters
          }
        } else {
          candidateCosts(random.nextInt(candidateCosts.size))._1
        }
      } else {
        candidateCosts.minBy(_._2)._1
      }
      if (iter % period == 0) {
        if (scala.math.abs(prevOptCost - optimalCost) < threshold) {
          hasConverged = true
        }
        prevOptCost = optimalCost
      }
      iter += 1
    }
  }

  def genBatchesByExploration(predictions: DataFrame): Unit = {
    val collectedPredictions = predictions.collect()
    val data = collectedPredictions.map { row =>
      val rowId = row.getAs[Int]("row_id")
      val features1 = featureColsForClustering1.map(col => 
        row.getAs[Any](col) match {
          case i: Int => i.toDouble
          case d: Double => d
          case _ => throw new IllegalArgumentException(s"Unsupported data type for column $col")
        }
      )
      val features2 = featureColsForClustering2.map(col => 
        row.getAs[Any](col) match {
          case i: Int => i.toDouble
          case d: Double => d
          case _ => throw new IllegalArgumentException(s"Unsupported data type for column $col")
        }
      )
      (rowId, features1, features2)
    }
    indexByRowId = data.map(_._1).zipWithIndex.toMap
    allPredictions = predictions
    val distinctTemplates = mutable.Set[Int]()
    collectedPredictions.foreach { row =>
      val rowId = row.getAs[Int]("row_id")
      // convert below into ints from predictionsMap.getOrElseUpdate(key1, mutable.Map[String, Double]())(key2) = value
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col0") = row.getAs[Double]("col0")
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col1") = row.getAs[Int]("col1").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col2") = row.getAs[Double]("col2")
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col3") = row.getAs[Int]("col3").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col4") = row.getAs[Int]("col4").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col5") = row.getAs[Int]("col5").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col6") = row.getAs[Int]("col6").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col7") = row.getAs[Int]("col7").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col8") = row.getAs[Double]("col8")
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col9") = row.getAs[Double]("col9")
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col10") = row.getAs[Int]("col10").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col11") = row.getAs[Int]("col11").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col12") = row.getAs[Int]("col12").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col13") = row.getAs[Int]("col13").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col14") = row.getAs[Int]("col14").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("col15") = row.getAs[Int]("col15").toDouble
      predictionsMap.getOrElseUpdate(rowId, mutable.Map[String, Double]())("prediction") = row.getAs[Double]("prediction")
      
      gSumOfInputCard += row.getAs[Double]("col0")
      gSumOfBaseCard += row.getAs[Int]("col1").toDouble
      gSumOfOutputCard += row.getAs[Double]("col2")
      gSumOfMapCount += row.getAs[Int]("col3").toDouble
      gSumOfFlatMapCount += row.getAs[Int]("col4").toDouble
      gSumOfFilterCount += row.getAs[Int]("col5").toDouble
      gSumOfJoinCount += row.getAs[Int]("col6").toDouble
      gSumOfReduceByKeyCount += row.getAs[Int]("col7").toDouble
      gSumOfShuffleInputCard += row.getAs[Double]("col8")
      gSumOfShuffleOutputCard += row.getAs[Double]("col9")
      gAvgRowLengthForInput += row.getAs[Int]("col10").toDouble
      gAvgRowLengthForBase += row.getAs[Int]("col11").toDouble
      gAvgRowLengthForOutput += row.getAs[Int]("col12").toDouble
      gAvgRowLengthForShuffleInput += row.getAs[Int]("col13").toDouble
      gAvgRowLengthForShuffleOutput += row.getAs[Int]("col14").toDouble
      gDistinctNumTemplates += row.getAs[Int]("col15").toDouble
      gSumOfElapsedTime += row.getAs[Double]("prediction")
      distinctTemplates += row.getAs[Int]("col15")
      gNumQueries += 1
    }
    gDistinctNumTemplates = distinctTemplates.size
    gAvgRowLengthForInput /= gNumQueries
    gAvgRowLengthForBase /= gNumQueries
    gAvgRowLengthForOutput /= gNumQueries
    gAvgRowLengthForShuffleInput /= gNumQueries
    gAvgRowLengthForShuffleOutput /= gNumQueries
    val optAssignments = if (numClusters1 < 0 && numClusters2 < 0) {
      if (featureColsForClustering1.contains("s0")) {
        val candidateCosts = List((8, 0), (16, 0))
          .map { initNumClusters => (initNumClusters, getCost(data, initNumClusters)) }
          .toArray
        explore(data, candidateCosts.minBy(_._2)._1)
      } else {
        val candidateCosts = List((3, 3), (6, 6))
          .map { initNumClusters => (initNumClusters, getCost(data, initNumClusters)) }
          .toArray
        explore(data, candidateCosts.minBy(_._2)._1)
      }
      
      optimalAssignments
    } else {
      var assignments = twoLevelClusteringForArray(data, (numClusters1, numClusters2))
      assignments
    }
    // Group by cluster and collect row IDs
    val fileName = clusterFilePath
    val path = Paths.get(fileName)
    if (Files.exists(path)) {
      Files.delete(path)
    }
    Files.createFile(path)
    val writer = new BufferedWriter(new FileWriter(fileName, true))
    val startTime = System.currentTimeMillis
    // using optassignments and data, printout the rowIds (data.map(_._1)) for each cluster_id in optAssignments
    val clusters = optAssignments.zip(data.map(_._1)).groupBy(_._1).mapValues(_.map(_._2))
    clusters.foreach { case (cluster, rowIds) =>
      writer.write(s"${rowIds.mkString(",")}\n")
    }
    writer.flush()
    writer.close()
  }

  def main(): Unit = {
    try {
      val model = trainModel(assembledTrainingData, regressionModelType)
      val similarityFilePath = s"/root/qbatcher/results/${dataset}-similarity.txt"
      val similarityMatrix: Array[Array[Double]] = Source.fromFile(similarityFilePath).getLines()
        .map(line => line.split("\\s+").map(_.toDouble))
        .toArray
      val broadcastSimMat = sc.broadcast(similarityMatrix)
      val getSimilarityValue = udf((index: Int, templateId: Int) => {
        broadcastSimMat.value(index)(templateId)
      })
      similarityMatrix.foreach { row =>
        // get the number of distinct values in row
        val numDistinctValues = row.distinct.length
      }
      var predictions = estimateQueryCost(model).cache()
      for (i <- similarityMatrix.indices) {
        predictions = predictions.withColumn(s"s$i", getSimilarityValue(lit(i), col("col15")))
      }
      genBatchesByExploration(predictions)
      System.exit(0)
    } catch {
      case e: Exception => 
        println(s"Error encountered: ${e.getMessage}")
        e.printStackTrace()  // This will print the full stack trace
        System.exit(1)  // Exit with an error code
    }
  }
}

val queryCostModel = new QueryCostModel(
  dataset,
  trainingFilePath,
  testFilePath,
  regressionModelType,
  numIter,
  clusteringAlgorithm,
  numClusters1,
  numClusters2,
  featureColumns,
  featureColsForClustering1,
  featureColsForClustering2,
  labelCol,
  outputFilePath,
  numQueries,
  gTrainingFilePathForBatchScheduler,
  gRegressionModelTypeForBatchScheduler,
  gFeatureColsForBatchScheduler,
  gNumIterForBatchScheduler,
  gNumBatchCol,
  gLabelColForBatchScheduler
)

queryCostModel.main()
