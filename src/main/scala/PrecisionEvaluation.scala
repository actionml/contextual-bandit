package org.template.classification

import io.prediction.controller.OptionAverageMetric
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.Evaluation

case class Precision(category:Double)
  extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header: String = s"Precision(category = $category)"

  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult)
  : Option[Double] = {
    if (predicted.category == category) {
      if (predicted.category == actual.category) {
        Some(1.0)  // True positive
      } else {
        Some(0.0)  // False positive
      }
    } else {
      None  // Unrelated case for calcuating precision
    }
  }
}

object PrecisionEvaluation extends Evaluation {
  engineMetric = (ClassificationEngine(), new Precision(category = 1.0))
}
