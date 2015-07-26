package org.apache.spark.mllib.linalg

object VectorUtil {

  implicit class VectorPublications(val vector : Vector) extends AnyVal {
    def toBreeze : breeze.linalg.Vector[scala.Double] = vector.toBreeze

  }

  implicit class BreezeVectorPublications(val breezeVector : breeze.linalg.Vector[Double]) extends AnyVal {
    def toSpark : Vector = Vectors.fromBreeze(breezeVector)

    def toDenseSpark : Vector = breezeVector match {
      case v: breeze.linalg.DenseVector[Double] => v.toSpark
      case _ => breezeVector.toDenseVector.toSpark
    }

  }
}