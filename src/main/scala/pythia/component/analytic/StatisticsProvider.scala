package pythia.component.analytic

import org.apache.spark.streaming.{Duration, Milliseconds}
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.streaming.dstream.DStream
import pythia.core._
import scala.reflect.ClassTag
import pythia.core.PropertyType._

class StatisticsProvider extends Component {

  def metadata = ComponentMetadata (
    name = "Statistic provider", category = "Analytic",
    inputs = Map (
      "Input" -> InputStreamMetadata(namedFeatures = List("Compute on", "Group by"))
    ),
    outputs = Map (
      "Output" -> OutputStreamMetadata(from = Some("Input"), namedFeatures =  List("Result"))
    ),
    properties = Map (
      "Operation" -> PropertyMetadata(STRING, acceptedValues = List("Mean", "Count")),
      "Window length (in ms)" -> PropertyMetadata(LONG)
    )
  )

  override protected def initStreams(context: Context): Map[String, DStream[Instance]] = {
    val windowLengthMs = context.properties( "Window length (in ms)")

    val aggregableStatistic = AggregableStatistic.withName(context.properties("Operation").as[String])
    val isGrouped = context.inputFeatureMapped("Input", "Group by")
    val out = if(windowLengthMs.isDefined) {
      computeStatistics(aggregableStatistic, isGrouped, context.dstream("Input", "Output"), Milliseconds(windowLengthMs.as[Long]))
    } else {
      computeStatistics(aggregableStatistic, isGrouped, context.dstream("Input", "Output"))
    }
    Map("Output" -> out)
  }

  def computeStatistics[T <: Any : ClassTag](
    aggregableStatistic: AggregableStatistic[T], isGrouped: Boolean,
    dstream: DStream[Instance]): DStream[Instance] = {

    val groupedInstances = dstream.map{instance =>
      val key = if(isGrouped) instance.inputFeature("Group by").as[String] else  "$GLOBAL$"
      (key, instance)
    }

    val states = groupedInstances
      .map{case (key, instance) => (key, instance.inputFeature("Compute on").as[Double])}
      .updateStateByKey[T]((newValues: Seq[Double], previousState: Option[T]) => {
        val state = previousState.getOrElse(aggregableStatistic.zero())
        val newState = aggregableStatistic.update(state, newValues)
        Some(newState)
      })

    groupedInstances
      .leftOuterJoin(states)
      .map {
        case (key, (instance, Some(state))) => instance.outputFeature("Result", aggregableStatistic.valueOf(state))
        case (key, (instance, None)) => instance.outputFeature("Result", null)
      }
  }

  def computeStatistics[T <: Any : ClassTag](
    aggregableStatistic: AggregableStatistic[T], isGrouped: Boolean,
    dstream: DStream[Instance],
    windowDuration: Duration): DStream[Instance] = {

    val groupedInstances = dstream.map{instance =>
      val key = if(isGrouped) instance.inputFeature("Group by").as[String] else  "$GLOBAL$"
      (key, instance)
    }

    val states = groupedInstances
      .map{case (key, instance) => (key, aggregableStatistic.init(instance.inputFeature("Compute on").as[Double]))}
      .reduceByKeyAndWindow((a: T, b: T) => aggregableStatistic.combine(a, b), windowDuration)

    groupedInstances
      .leftOuterJoin(states)
      .map {
      case (key, (instance, Some(state))) => instance.outputFeature("Result", aggregableStatistic.valueOf(state))
      case (key, (instance, None)) => instance.outputFeature("Result", null)
    }
  }
}

case class MeanState(sum: Double, count: Long)

class AggregableMean extends AggregableStatistic[MeanState] {
  def init(value: Double) = MeanState(value, 1)
  def combine(a: MeanState, b: MeanState) = MeanState(a.sum + b.sum, a.count + b.count)
  def valueOf(state: MeanState): Double = state.sum / state.count.toDouble
  def zero() = MeanState(0.0, 0)
  def update(state: MeanState, values: Seq[Double]) = MeanState(state.sum + values.foldLeft(0.0)(_+_), state.count + values.size)
}

class AggregableCount extends AggregableStatistic[Double] {
  def init(value: Double) = 1.0
  def combine(a: Double, b: Double) = a + b
  def valueOf(state: Double): Double = state
  def zero() = 0.0
  def update(state: Double, values: Seq[Double]): Double = state + values.size
}

trait AggregableStatistic[T] extends Serializable {
  def init(value: Double): T
  def combine(a: T, b: T): T
  def zero(): T
  def valueOf(state: T): Double
  def update(state: T, values: Seq[Double]): T
}

object AggregableStatistic {
  def withName(name: String): AggregableStatistic[_] = name match {
    case "Count" => new AggregableCount()
    case "Mean" => new AggregableMean()
  }
}