import scala.io.Source

import java.io._
import java.nio.file.{Files, Paths}

class SimpleMLP(
  numInputs: Int = 20,
  numHidden: Int = 20,
  numOutputs: Int = 1,
  maxIter: Int = -1,
  l2Reg: Double = 0.005,
  convergenceThreshold: Double = 1e-6,  // New parameter for convergence threshold
  checkConvergenceEvery: Int = 10,
  learningRate: Double = 0.1,
  blockSize: Int = 32,
  weightFilePath: String) {
  val rng = new scala.util.Random(0)
  // Initialize weights and biases with random values
  var W_ih: Array[Array[Double]] = Array.fill(numInputs, numHidden)(rng.nextDouble)
  var b_h: Double = rng.nextDouble  // single bias for the hidden layer
  var W_ho: Array[Array[Double]] = Array.fill(numHidden, numOutputs)(rng.nextDouble)
  var b_o: Double = rng.nextDouble  // single bias for the output layer
  
  // Initialize Adam variables
  val beta1: Double = 0.9
  val beta2: Double = 0.999
  val epsilon: Double = 1e-8
  var mW_ih: Array[Array[Double]] = Array.fill(numInputs, numHidden)(0.0)
  var vW_ih: Array[Array[Double]] = Array.fill(numInputs, numHidden)(0.0)
  var mb_h: Double = 0.0
  var vb_h: Double = 0.0
  var mW_ho: Array[Array[Double]] = Array.fill(numHidden, numOutputs)(0.0)
  var vW_ho: Array[Array[Double]] = Array.fill(numHidden, numOutputs)(0.0)
  var mb_o: Double = 0.0
  var vb_o: Double = 0.0
  var t: Int = 0

  def relu(x: Double): Double = math.max(0, x)
  
  def forward(input: Array[Double]): (Seq[Double], Seq[Double]) = {
    val hiddenInput = for (i <- 0 until numHidden) yield 
      (0 until numInputs).map(j => input(j) * W_ih(j)(i)).sum + b_h
    //val hiddenInput = for (i <- 0 until numHidden) yield 
    //  (0 until numInputs).map(j => input(j) * W_ih(j)(i).toDouble).sum + b_h.toDouble
    val hiddenOutput = hiddenInput.map(relu)  // ReLU activation
    val finalInput = (0 until numOutputs).map(i => 
      (0 until numHidden).map(j => hiddenOutput(j) * W_ho(j)(i)).sum + b_o)
    val finalOutput = finalInput.map(relu)
    (hiddenOutput, finalOutput)
  }
  
  def backprop_gd(input: Array[Double], target: Double, hiddenOutput: Seq[Double], finalOutput: Seq[Double]): Unit = {
    val outputErrors = finalOutput.zip(Array(target)).map{ case (o, t) => o - t }
    val hiddenErrors = (0 until numHidden).map(i => 
      (0 until numOutputs).map(j => outputErrors(j) * W_ho(i)(j)).sum)
    // Update weights and biases
    for(i <- (0 until numOutputs).par; j <- (0 until numHidden).par) {
      W_ho(j)(i) -= learningRate * (outputErrors(i) * hiddenOutput(j) + l2Reg * W_ho(j)(i))
    }
    b_o -= learningRate * outputErrors.sum  // Update single bias for the output layer
    for(i <- (0 until numHidden).par; j <- (0 until numInputs).par) {
      W_ih(j)(i) -= learningRate * (hiddenErrors(i) * input(j) + l2Reg * W_ih(j)(i))
    }
    b_h -= learningRate * hiddenErrors.sum  // Update single bias for the hidden layer
  }

  def backprop(input: Array[Double], target: Double, hiddenOutput: Seq[Double], finalOutput: Seq[Double]): Unit = {
    val outputErrors = finalOutput.zip(Array(target)).map{ case (o, t) => o - t }
    val hiddenErrors = (0 until numHidden).map(i => 
      (0 until numOutputs).map(j => outputErrors(j) * W_ho(i)(j)).sum)

    t += 1  // Increment timestep

    // Update first and second moment estimates for weights and biases between hidden and output layers
    for(i <- (0 until numOutputs).par; j <- (0 until numHidden).par) {
      val grad_ho = outputErrors(i) * hiddenOutput(j) + l2Reg * W_ho(j)(i)
      mW_ho(j)(i) = beta1 * mW_ho(j)(i) + (1 - beta1) * grad_ho
      vW_ho(j)(i) = beta2 * vW_ho(j)(i) + (1 - beta2) * grad_ho * grad_ho
    }
    
    val grad_bo = outputErrors.sum + l2Reg * b_o
    mb_o = beta1 * mb_o + (1 - beta1) * grad_bo
    vb_o = beta2 * vb_o + (1 - beta2) * grad_bo * grad_bo

    // Update first and second moment estimates for weights and biases between input and hidden layers
    for(i <- (0 until numHidden).par; j <- (0 until numInputs).par) {
      val grad_ih = hiddenErrors(i) * input(j) + l2Reg * W_ih(j)(i)
      mW_ih(j)(i) = beta1 * mW_ih(j)(i) + (1 - beta1) * grad_ih
      vW_ih(j)(i) = beta2 * vW_ih(j)(i) + (1 - beta2) * grad_ih * grad_ih
    }
    
    val grad_bh = hiddenErrors.sum + l2Reg * b_h
    mb_h = beta1 * mb_h + (1 - beta1) * grad_bh
    vb_h = beta2 * vb_h + (1 - beta2) * grad_bh * grad_bh

    // Compute bias-corrected moment estimates and apply Adam updates
    for(i <- (0 until numOutputs).par; j <- (0 until numHidden).par) {
      val mHatW_ho = mW_ho(j)(i) / (1 - math.pow(beta1, t))
      val vHatW_ho = vW_ho(j)(i) / (1 - math.pow(beta2, t))
      W_ho(j)(i) -= learningRate * mHatW_ho / (math.sqrt(vHatW_ho) + epsilon)
    }
    
    val mHat_bo = mb_o / (1 - math.pow(beta1, t))
    val vHat_bo = vb_o / (1 - math.pow(beta2, t))
    b_o -= learningRate * mHat_bo / (math.sqrt(vHat_bo) + epsilon)
    
    for(i <- (0 until numHidden).par; j <- (0 until numInputs).par) {
      val mHatW_ih = mW_ih(j)(i) / (1 - math.pow(beta1, t))
      val vHatW_ih = vW_ih(j)(i) / (1 - math.pow(beta2, t))
      W_ih(j)(i) -= learningRate * mHatW_ih / (math.sqrt(vHatW_ih) + epsilon)
    }
    
    val mHat_bh = mb_h / (1 - math.pow(beta1, t))
    val vHat_bh = vb_h / (1 - math.pow(beta2, t))
    b_h -= learningRate * mHat_bh / (math.sqrt(vHat_bh) + epsilon)
  }

  def computeLoss(data: Array[(Array[Double], Double)]): Double = {
    var totalLoss = 0.0
    for ((input, target) <- data) {
      // Perform a forward pass to get the output of the network
      val (_, finalOutput) = forward(input)
      
      // Compute the Mean Squared Error (MSE) loss
      val mseLoss = finalOutput.zip(Array(target)).map { case (o, t) => 0.5 * (o - t) * (o - t) }.sum
      
      // Compute the L2 regularization loss
      val l2Loss = l2Reg * (W_ih.flatten.map(w => w * w).sum + W_ho.flatten.map(w => w * w).sum)
      
      // Accumulate the total loss
      totalLoss += mseLoss + l2Loss
    }
    totalLoss
  }

  def featureImportance(data: Array[(Array[Double], Double)]): Array[Double] = {
    val baselineLoss = computeLoss(data)
    val importances = Array.fill(data(0)._1.length)(0.0)  // Initialize an array to hold feature importances
    for (i <- importances.indices) {
      // Permute feature i across all data points
      val permutedData = data.indices.map { idx =>
        val permutedInput = data(idx)._1.clone()
        permutedInput(i) = data((idx + rng.nextInt(data.length)) % data.length)._1(i)
        (permutedInput, data(idx)._2)
      }.toArray
      val permutedLoss = computeLoss(permutedData)
      importances(i) = permutedLoss - baselineLoss  // Higher loss indicates higher importance
    }
    importances
  }


  def kFoldCrossValidation(data: Array[(Array[Double], Double)], k: Int = 5): (Double, Double, Map[String, Double]) = {
    import java.util.Arrays
    import org.apache.commons.math3.stat.correlation.PearsonsCorrelation

    val shuffledData = rng.shuffle(data.toList)
    val foldSize = data.length / k
    val remainder = data.length % k
    val folds = Array.fill(k)(Array.empty[(Array[Double], Double)])

    for (i <- 0 until k) {
      val startIdx = i * foldSize + math.min(i, remainder)
      val endIdx = startIdx + foldSize + (if (i < remainder) 1 else 0)
      folds(i) = shuffledData.slice(startIdx, endIdx).toArray
    }

    val rmseValues = Array.fill[Double](k)(0.0)
    val pearsonValues = Array.fill[Double](k)(0.0)
    val accuracyErrors = Array.newBuilder[Double]
    val accuracyErrors2 = Array.newBuilder[Double]

    for (i <- folds.indices) {
      val validationFold = folds(i)
      val trainingFolds = folds.slice(0, i) ++ folds.slice(i + 1, folds.length)
      val trainingData = trainingFolds.flatten
      train(trainingData)
      val validationPredictions = validationFold.map { case (input, target) =>
        val prediction = forward(input)._2(0)
        (prediction, target)
      }
      val rmse = math.sqrt(validationPredictions.map { case (prediction, target) =>
        math.pow(prediction - target, 2)
      }.sum / validationFold.length)
      rmseValues(i) = rmse

      val predictions = validationPredictions.map(_._1)
      val targets = validationPredictions.map(_._2)
      val pearson = new PearsonsCorrelation().correlation(predictions.toArray, targets.toArray)
      pearsonValues(i) = pearson

      // Collect accuracy errors
      val foldAccuracyErrors = validationPredictions.map { case (prediction, target) =>
        math.abs((target - prediction) / target) * 100
      }
      accuracyErrors ++= foldAccuracyErrors

      val foldAccuracyErrors2 = validationPredictions.map { case (prediction, target) =>
        prediction.toDouble / target.toDouble
      }
      accuracyErrors2 ++= foldAccuracyErrors2
    }

    val averageRMSE = rmseValues.sum / rmseValues.length
    val averagePearson = pearsonValues.sum / pearsonValues.length
    val allAccuracyErrors = accuracyErrors.result()
    val allAccuracyErrors2 = accuracyErrors2.result()
    Arrays.sort(allAccuracyErrors)  // Sort accuracy errors
    Arrays.sort(allAccuracyErrors2)

    // Compute percentiles
    val percentiles = Map(
      "5th" -> allAccuracyErrors((0.05 * allAccuracyErrors.length).toInt),
      "25th" -> allAccuracyErrors((0.25 * allAccuracyErrors.length).toInt),
      "50th" -> allAccuracyErrors((0.50 * allAccuracyErrors.length).toInt),
      "75th" -> allAccuracyErrors((0.75 * allAccuracyErrors.length).toInt),
      "95th" -> allAccuracyErrors((0.95 * allAccuracyErrors.length).toInt)
    )
    val percentiles2 = Map(
      "5th" -> allAccuracyErrors2((0.05 * allAccuracyErrors2.length).toInt),
      "25th" -> allAccuracyErrors2((0.25 * allAccuracyErrors2.length).toInt),
      "50th" -> allAccuracyErrors2((0.50 * allAccuracyErrors2.length).toInt),
      "75th" -> allAccuracyErrors2((0.75 * allAccuracyErrors2.length).toInt),
      "95th" -> allAccuracyErrors2((0.95 * allAccuracyErrors2.length).toInt)
    )

    (averageRMSE, averagePearson, percentiles)
  }

  def train(data: Array[(Array[Double], Double)]): Unit = {
    val path = Paths.get(weightFilePath)
    if (Files.exists(path)) {
      loadWeights(weightFilePath)
      return
    }
    var prevTotalLoss = Double.MaxValue  // Initialize with a large value
    var iter = 0
    var converged = false
    val startTime = System.currentTimeMillis()

    val numBatches = (data.length + blockSize - 1) / blockSize  // Calculate the number of batches
    val batches = Array.tabulate(numBatches) { i =>
      data.slice(i * blockSize, (i + 1) * blockSize)
    }
    while (maxIter == -1 && !converged || iter < maxIter) {
      val totalLossAcc = new java.util.concurrent.atomic.AtomicLong(0L)  // Atomic accumulator for total loss

      batches.par.foreach { batch =>  // Process batches in parallel
        val batchLoss = batch.map { case (input, target) =>
          val (hiddenOutput, finalOutput) = forward(input)

          // Compute loss (Mean Squared Error) and L2 regularization
          val loss = finalOutput.zip(Array(target)).map{ case (o, t) => 0.5 * (o - t) * (o - t) }.sum
          val l2Loss = l2Reg * (W_ih.flatten.map(w => w * w).sum + W_ho.flatten.map(w => w * w).sum)
          
          backprop(input, target, hiddenOutput, finalOutput)

          loss + l2Loss
        }.sum  // Sum the loss over the batch
        
        totalLossAcc.addAndGet((batchLoss * 1e6).toLong)  // Atomically accumulate total loss over all batches
      }
      
      val totalLoss = totalLossAcc.get() / 1e6  // Get the total loss and convert back to double
      
      // Check convergence criteria if maxIter is -1
      if (maxIter == -1 && iter % checkConvergenceEvery == 0) {
        val lossChange = math.abs(totalLoss - prevTotalLoss)
        converged = lossChange < convergenceThreshold
        prevTotalLoss = totalLoss  // Update previous total loss
      }
      
      iter += 1  // Increment iteration counter
    }
    saveWeights(weightFilePath)
    //kFoldCrossValidation(data, 5)
  }

  def test(data: Array[Array[Double]]): Array[Array[Double]] = {
    val startTime = System.currentTimeMillis()
    val tmp = data.par.map { case input =>
      forward(input)._2.toArray
    }
    tmp.toArray
  }

  def saveWeights(filePath: String): Unit = {
    val pw = new PrintWriter(new File(filePath))
    // Save weights and biases in a simple text format
    pw.write(s"b_h $b_h\n")
    pw.write(s"b_o $b_o\n")
    for (i <- W_ih.indices; j <- W_ih(i).indices) {
      pw.write(s"W_ih $i $j ${W_ih(i)(j)}\n")
    }
    for (i <- W_ho.indices; j <- W_ho(i).indices) {
      pw.write(s"W_ho $i $j ${W_ho(i)(j)}\n")
    }
    pw.close()
  }

  def loadWeights(filePath: String): Unit = {
    val source = Source.fromFile(filePath)
    val lines = source.getLines()
    for (line <- lines) {
      val tokens = line.split(" ")
      tokens(0) match {
        case "b_h" => b_h = tokens(1).toDouble
        case "b_o" => b_o = tokens(1).toDouble
        case "W_ih" => W_ih(tokens(1).toInt)(tokens(2).toInt) = tokens(3).toDouble
        case "W_ho" => W_ho(tokens(1).toInt)(tokens(2).toInt) = tokens(3).toDouble
        case _ => // Do nothing for unrecognized lines
      }
    }
    source.close()
  }

  def splitData(data: Array[(Array[Double], Double)], ratio: Double = 0.8): (Array[(Array[Double], Double)], Array[(Array[Double], Double)]) = {
    val shuffled = rng.shuffle(data.toList)
    val splitPoint = (shuffled.size * ratio).toInt
    (shuffled.take(splitPoint).toArray, shuffled.drop(splitPoint).toArray)
  }

  def trainSingleIteration(data: Array[(Array[Double], Double)]): Unit = {

    // Process a single batch of data
    val batchLoss = data.map { case (input, target) =>
      val (hiddenOutput, finalOutput) = forward(input)

      // Compute loss (Mean Squared Error) and L2 regularization
      val loss = finalOutput.zip(Array(target)).map { case (o, t) => 0.5 * (o - t) * (o - t) }.sum
      val l2Loss = l2Reg * (W_ih.flatten.map(w => w * w).sum + W_ho.flatten.map(w => w * w).sum)
      
      // Perform backpropagation to update weights
      backprop(input, target, hiddenOutput, finalOutput)

      loss + l2Loss
    }.sum  // Sum the loss over this batch

    // Since this is a single iteration, we do not need to check for convergence or keep track of previous total loss.
    // The calling function will handle the evaluation of loss and decide whether to continue training or not.
    
    // Optionally, if you want to save weights after every iteration, uncomment the line below.
    // saveWeights(weightFilePath)
  }

  def trainAndEvaluate(data: Array[(Array[Double], Double)]): (Int, Double, Double) = {
    // Split the data
    val (trainingData, testData) = splitData(data)

    // Initialize variables to keep track of the best iteration and loss
    var bestIter = -1
    var bestTrainingLoss = Double.MaxValue
    var bestTestLoss = Double.MaxValue
    var iter = 0
    var converged = false

    // Loop for training and evaluation
    while (maxIter == -1 && !converged || iter < maxIter) {
      // Train on the current batch
      trainSingleIteration(trainingData)

      // Evaluate on training data
      val trainingLoss = computeLoss(trainingData)

      // Evaluate on test data
      val testLoss = computeLoss(testData)

      // Update best loss and iteration
      
      if (trainingLoss + testLoss < bestTrainingLoss + bestTestLoss) {
        bestTrainingLoss = trainingLoss
        bestTestLoss = testLoss
        bestIter = iter
      }

      iter += 1
    }

    // Return the best iteration and the losses
    (bestIter, bestTrainingLoss, bestTestLoss)
  }
}
