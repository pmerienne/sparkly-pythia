package sparkly.component.classifier

import sparkly.core._
import org.apache.spark.streaming.dstream.DStream
import sparkly.core.Context
import sparkly.core.InputStreamMetadata
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.classification.StreamingLogisticRegressionWithSGD
import org.apache.spark.mllib.regression.LabeledPoint
import scala.util.Try
import sparkly.component.common.RunningAccuracy

class LogisticBinaryClassifierWithSGD  extends Component {

  override def metadata: ComponentMetadata = ComponentMetadata (
    name = "Logistic binary classifier",
    description =
      """
        |Binary classifier based on a logistic regression model with SGD.
      """.stripMargin,
    inputs = Map (
      "Train" -> InputStreamMetadata(namedFeatures = Map("Label" -> FeatureType.BOOLEAN), listedFeatures = Map("Features" -> FeatureType.CONTINUOUS)),
      "Predict" -> InputStreamMetadata(listedFeatures = Map("Features" -> FeatureType.CONTINUOUS))
    ),outputs = Map (
      "Predictions" -> OutputStreamMetadata(from = Some("Predict"), namedFeatures = Map("Label" -> FeatureType.BOOLEAN))
    ), properties = Map (
      "Step size" -> PropertyMetadata(PropertyType.DECIMAL, defaultValue = Some(0.1), description = "Step size for gradient descent"),
      "Iterations" -> PropertyMetadata(PropertyType.INTEGER, defaultValue = Some(50), description = "Number of iterations of gradient descent to run per batch"),
      "Batch fraction" -> PropertyMetadata(PropertyType.DECIMAL, defaultValue = Some(1.0), description = "Fraction of each batch to use for updates"),
      "Regularization" -> PropertyMetadata(PropertyType.DECIMAL, defaultValue = Some(0.0), description = "Regularization parameter")
    ), monitorings = Map("Accuracy" -> MonitoringMetadata(ChartType.LINES, values = List("Accuracy"), primaryValues = List("Accuracy"), unit = "%"))
  )

  override protected def initStreams(context: Context): Map[String, DStream[Instance]] = {
    val stepSize = context.properties("Step size").as[Double]
    val iterations = context.properties("Iterations").as[Int]
    val miniBatchFraction = context.properties("Batch fraction").as[Double]
    val regularization = context.properties("Regularization").as[Double]
    val initialWeights = Vectors.zeros(context.inputSize("Train", "Features"))

    val model = new StreamingLogisticRegressionWithSGD()
      .setStepSize(stepSize)
      .setNumIterations(iterations)
      .setMiniBatchFraction(miniBatchFraction)
      .setRegParam(regularization)
      .setInitialWeights(initialWeights)

    var runningAccuracy = RunningAccuracy[Double]()
    val accuracyMonitoring = context.createMonitoring[Map[String, Double]]("Accuracy")

    val train = context.dstream("Train").map{ i =>
      val label = i.inputFeature("Label").asDouble
      val features = Vectors.dense(i.inputFeatures("Features").asDoubleArray)
      LabeledPoint(label, features)
    }.cache()

    // Test
    train.foreachRDD{ (rdd, time) =>
      Try {
        val actual = model.latestModel().predict(rdd.map(_.features))
        val expected = rdd.map(_.label)
        runningAccuracy = runningAccuracy.update(expected, actual)
        accuracyMonitoring.add(time.milliseconds, Map("Accuracy" -> runningAccuracy.value * 100.0))
      }
    }

    // Then train
    model.trainOn(train)

    // Predict
    val predictions = context
      .dstream("Predict", "Predictions").map{ i =>
        val features = Vectors.dense(i.inputFeatures("Features").asDoubleArray)
        val label = model.latestModel().predict(features) > 0.5
        i.outputFeature("Label", label)
      }

    Map("Predictions" -> predictions)
  }
}