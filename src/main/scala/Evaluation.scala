package org.template.classification

import io.prediction.controller.AverageMetric
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EngineParams
import io.prediction.controller.EngineParamsGenerator
import io.prediction.controller.Evaluation

case class Accuracy
  extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult)
  : Double = (if (predicted.category == actual.category) 1.0 else 0.0)
}

object AccuracyEvaluation extends Evaluation {
  // Define Engine and Metric used in Evaluation
  engineMetric = (ClassificationEngine(), new Accuracy())
}

object EngineParamsList extends EngineParamsGenerator {
  // Define list of EngineParams used in Evaluation

  // First, we define the base engine params. It specifies the appId from which
  // the data is read, and a evalK parameter is used to define the
  // cross-validation.
  private[this] val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(appName = "INVALID_APP_NAME"))

  // Second, we specify the engine params list by explicitly listing all
  // algorithm parameters. In this case, we evaluate 3 engine params, each with
  // a different algorithm params value.
  engineParamsList = Seq(
    baseEP.copy(algorithmParamsList = Seq(("VWlogisticSGD", AlgorithmParams(10, 0.1, 1.0, 1, "model.vw", "n",10)))),
    baseEP.copy(algorithmParamsList = Seq(("VWlogisticSGD", AlgorithmParams(100, 0.01, 1.0, 1, "model.vw", "n",10)))),
    baseEP.copy(algorithmParamsList = Seq(("VWlogisticSGD", AlgorithmParams(1, 0.1, 1.0, 1, "model.vw", "n",10)))))
}
