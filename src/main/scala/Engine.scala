package org.template.classification

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

class Query(
  val text: String
) extends Serializable

class PredictedResult(
  val category: String,
  val confidence: Double
) extends Serializable

class ActualResult(
  val category: String
) extends Serializable

object ClassificationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("VWlogisticSGD" -> classOf[VowpalPageVariantRecommenderAlgorithm]),
      classOf[Serving])
  }
}
