import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.ml.regression.LinearRegressionModel
import org.apache.spark.ml.regression.DecisionTreeRegressor
import org.apache.spark.ml.regression.DecisionTreeRegressionModel
import org.apache.spark.ml.regression.RandomForestRegressor
import org.apache.spark.ml.regression.RandomForestRegressionModel
import org.apache.spark.ml.regression.GBTRegressor
import org.apache.spark.ml.regression.GBTRegressionModel
import org.apache.spark.ml.classification.MultilayerPerceptronClassifier
import org.apache.spark.ml.classification.MultilayerPerceptronClassificationModel
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
import org.apache.spark.ml.feature.StandardScaler
import org.apache.spark.ml.feature.StandardScalerModel
import org.apache.spark.ml.tuning.CrossValidatorModel
import org.apache.spark.sql.functions._
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.apache.spark.sql.{SparkSession, Row}
import org.apache.spark.sql.types._

import java.util.Arrays
import java.io.File

class WorkloadCostModel(
    trainingFilePath: String,
    regressionModelType: String,
    numIter: Int, // 20
    featureColumns: Array[String], // "col0,...,col19"
    labelCol: String // col19
) {
  var trainingData: DataFrame = _
  var elasticNetRegressor: LinearRegressionModel = null
  var decisionTreeRegressor: DecisionTreeRegressionModel = null
  var randomForestRegressor: RandomForestRegressionModel = null
  var gbtRegressor: GBTRegressionModel = null
  var mlpRegressor: MultilayerPerceptronClassificationModel = null
  var simpleMlpRegressor: SimpleMLP = null
  var elapsedTime: Float = 0

  val scaler = new StandardScaler()
    .setInputCol("features")
    .setOutputCol("scaledFeatures")
    .setWithStd(true)
    .setWithMean(true)
  var scalerModel: StandardScalerModel = null
  
  def trainModel(
      scaledTrainingData: DataFrame
  ): Any = {
    if (regressionModelType == "simpleMlp") {
      val collectedTrainingData = scaledTrainingData.collect()
      // convert collectedTrainingData to Array[(Array[Double], Double)]
      val trainingData = collectedTrainingData.map { row =>
        val features = row.getAs[org.apache.spark.ml.linalg.Vector]("scaledFeatures").toArray
        val label = row.getAs[Double](labelCol)
        (features, label)
      }
      simpleMlpRegressor = new SimpleMLP(
        featureColumns.size,
        20,
        1,
        30000,
        0.005,
        1e-6,
        10,
        0.01,
        32,
        s"/root/qbatcher/results/single-batch-cost-estimator-weights-${dataset}.txt"
      )
      simpleMlpRegressor.train(trainingData)
      simpleMlpRegressor
    } else {
      val regressionModel = this.regressionModelType match {
        case "elasticNet" =>
          new LinearRegression()
            .setLabelCol(labelCol)
            .setFeaturesCol("scaledFeatures")
            .setMaxIter(30000)
            .setElasticNetParam(0.5)
            .setRegParam(1.0)
            .setFitIntercept(true)
        case "decisionTree" =>
          new DecisionTreeRegressor()
            .setLabelCol(labelCol)
            .setFeaturesCol("scaledFeatures")
            .setMaxDepth(15)
        case "randomForest" =>
          new RandomForestRegressor()
            .setLabelCol(labelCol)
            .setFeaturesCol("scaledFeatures")
            .setNumTrees(20)
            .setMaxDepth(5)
        case "gbt" =>
          new GBTRegressor()
            .setLabelCol(labelCol)
            .setFeaturesCol("scaledFeatures")
            .setMaxIter(20)
            .setMaxDepth(5)
        case "mlp" =>
          val layers = Array[Int](this.featureColumns.size, 2000, 3000)
          new MultilayerPerceptronClassifier()
            .setLayers(layers)
            .setLabelCol(labelCol)
            .setFeaturesCol("scaledFeatures")
            .setBlockSize(1)
            .setSeed(0L)
            //.setMaxIter(numIter)
        case _ =>
          throw new IllegalArgumentException(
            s"Unsupported regression model type: $this.regressionModelType"
          )
      }
      if (true) {
        val paramGrid = new ParamGridBuilder().build()

        // Set up the regression evaluator
        val evaluator = new RegressionEvaluator()
          .setLabelCol(labelCol)
          .setPredictionCol("prediction")
          .setMetricName("rmse")  // Root Mean Square Error

        // Set up 5-fold cross-validation
        val cv = new CrossValidator()
          .setEstimator(regressionModel)
          .setEvaluator(evaluator)
          .setEstimatorParamMaps(paramGrid)
          .setNumFolds(5)
          .setParallelism(2)  // Use parallelism to speed up cross-validation

        // Train the model
        val cvModel = cv.fit(scaledTrainingData)
        regressionModel.fit(scaledTrainingData)
      } else {
        regressionModel.fit(scaledTrainingData)
      }
    }
  }

  def fit(): Unit = {
    try {
      trainingData = spark.read
        .option("header", "false")
        .option("inferSchema", "true")
        .csv(s"${trainingFilePath}")
        .filter(abs(col("_c18") - 1.0) < 1e-9 && abs(col("_c19")) < 1e-9)
        .repartition(spark.sparkContext.defaultParallelism)
      var columns = trainingData.columns
      val renamedTrainingData = columns.zipWithIndex
        .foldLeft(trainingData) { (tempData, col) =>
          tempData.withColumnRenamed(col._1, s"col${col._2}")
        }
        .na
        .drop()
        //.withColumn(labelCol, (col(labelCol) * 10).cast("int"))
      val featureCols =
        featureColumns // renamedTrainingData.columns.dropRight(1)
      val assembler =
        new VectorAssembler().setInputCols(featureCols).setOutputCol("features")
      val assembledTrainingData = assembler.transform(renamedTrainingData)
      scalerModel = scaler.fit(assembledTrainingData)
      val scaledTrainingData = scalerModel.transform(assembledTrainingData)
      this.regressionModelType match {
        case "elasticNet" =>
          elasticNetRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[LinearRegressionModel]
        case "decisionTree" =>
          decisionTreeRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[DecisionTreeRegressionModel]
        case "randomForest" =>
          randomForestRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[RandomForestRegressionModel]
        case "gbt" =>
          gbtRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[GBTRegressionModel]
        case "mlp" =>
          mlpRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[MultilayerPerceptronClassificationModel]
        case "simpleMlp" =>
          simpleMlpRegressor = trainModel(scaledTrainingData)
            .asInstanceOf[SimpleMLP]
      }
    } catch {
      case e: Exception =>
        println(s"Error encountered: ${e.getMessage}")
        e.printStackTrace() // This will print the full stack trace
        System.exit(1) // Exit with an error code
    }
  }

  def estimateWorkloadCost(
      fullFeatures: Seq[
        (
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double,
            Double
        )
      ]
  ): Double = {
    val features = fullFeatures
    val df = spark.createDataFrame(features).toDF(featureColumns: _*)
    val assembler = new VectorAssembler()
      .setInputCols(featureColumns)
      .setOutputCol("features")
    val assembledTestData = assembler.transform(df)
    val scaledTestData = scalerModel.transform(assembledTestData)
    if (regressionModelType == "simpleMlp") {
      val input = scaledTestData.collect().map { row =>
        val features = row.getAs[org.apache.spark.ml.linalg.Vector]("scaledFeatures").toArray.map(_.toDouble)
        features
        }.toArray
      val predictions = simpleMlpRegressor.test(input)
      predictions(0)(0)
    } else {
      val regressionModel = this.regressionModelType match {
        case "elasticNet" =>
          this.elasticNetRegressor
        case "decisionTree" =>
          this.decisionTreeRegressor
        case "randomForest" =>
          this.randomForestRegressor
        case "gbt" =>
          this.gbtRegressor      
        case "mlp" =>
          this.mlpRegressor
        case _ =>
          throw new IllegalArgumentException(
            s"Unsupported regression model type: $this.regressionModelType"
          )
      }
      val startTime = System.currentTimeMillis()
      val predictions = regressionModel.transform(scaledTestData)
      val estimatedCost = predictions.select("prediction").first().getDouble(0)
      estimatedCost
    }
  }
}
