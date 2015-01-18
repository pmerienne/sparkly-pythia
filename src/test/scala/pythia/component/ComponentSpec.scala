package pythia.component

import java.nio.file.{Path, Files}

import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import pythia.core._
import pythia.testing._
import java.io.File
import org.apache.commons.io.FileUtils

trait ComponentSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Eventually {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, org.scalatest.time.Seconds)), interval = scaled(Span(100, Millis)))

  var sc: SparkContext = null
  var ssc: StreamingContext = null
  var checkpointDirectory: Path = null

  override def beforeEach() {
    val conf = new SparkConf()
      .setMaster("local[8]")
      .setAppName("test-" + this.getClass.getSimpleName)


    checkpointDirectory = Files.createTempDirectory("pythia-test")
    ssc = new StreamingContext(conf, Milliseconds(200))
    ssc.checkpoint(checkpointDirectory.toString)
  }

  override def afterEach() {
    ssc.stop()
    ssc.awaitTermination(2000)
    FileUtils.deleteDirectory(checkpointDirectory.toFile)
  }

  def deployComponent(componentConfiguration: ComponentConfiguration, inputs: Map[String, DStream[Instance]]): Map[String, InspectedStream] = {
    val component = Class.forName(componentConfiguration.clazz).newInstance.asInstanceOf[Component]
    val outputs = component.init(ssc, componentConfiguration, inputs)
    val inspectedOutputs = outputs.map{case (name, dstream) => (name, InspectedStream(dstream))}

    ssc.start()
    inspectedOutputs
  }

  def mockedStream() = MockStream(ssc)

}

