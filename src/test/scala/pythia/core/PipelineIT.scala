package pythia.core

import org.apache.spark.streaming._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, FlatSpec}
import pythia.component.classifier.Perceptron
import pythia.component.{CsvSource, Normalizer}
import pythia.core._
import pythia.testing._

class PipelineIT extends FlatSpec with Matchers with Eventually with SpamData {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, org.scalatest.time.Seconds)), interval = scaled(Span(100, Millis)))

  "Pipeline" should "build and connect components together" in {
    val pipelineConfig = PipelineConfiguration (
      name = "test",
      components = List (
        ComponentConfiguration (
          id = "csv_source",
          name = "Train data",
          clazz = classOf[CsvSource].getName,
          properties = Map (
            "File" -> "src/test/resources/spam.data"
          ),
          outputs = Map (
            "Instances" -> StreamConfiguration(selectedFeatures = Map("Features" -> (labelName::featureNames)))
          )
        ),
        ComponentConfiguration (
          id = "normalizer",
          name = "Normalizer",
          clazz = classOf[Normalizer].getName,
          inputs = Map (
            "Input" -> StreamConfiguration(selectedFeatures = Map("Features" -> featureNames))
          )
        ),
        ComponentConfiguration (
          id = "perceptron",
          name = "Learner",
          clazz = classOf[Perceptron].getName,
          properties = Map (
            "Bias" -> "1.0",
            "Learning rate" -> "1.0"
          ),
          inputs = Map (
            "Train" -> StreamConfiguration(mappedFeatures = Map("Label" -> labelName), selectedFeatures = Map("Features" -> featureNames)),
            "Prediction query" -> StreamConfiguration(selectedFeatures = Map("Features" -> featureNames))
          ),
          outputs = Map (
            "Prediction result" -> StreamConfiguration(mappedFeatures = Map("Label" -> "prediction")),
            "Accuracy" -> StreamConfiguration(mappedFeatures = Map("Accuracy" -> "Accuracy"))
          )
        )
      ),
      connections = List (
        ConnectionConfiguration("csv_source", "Instances", "normalizer", "Input"),
        ConnectionConfiguration("normalizer", "Output", "perceptron", "Train")
      )
    )

    val pipeline = Pipeline(pipelineConfig)

    // System init
    val ssc = new StreamingContext("local", "Test", Seconds(1))
    ssc.checkpoint("/tmp")

    val availableStreams = pipeline.build(ssc)
    val accuracies = InspectedStream(availableStreams(("perceptron" ,"Accuracy")))

    ssc.start()
    eventually {
      accuracies.instances.last.rawFeatures("Accuracy").asInstanceOf[Double] should be > 0.80
    }
    ssc.stop()

  }
}