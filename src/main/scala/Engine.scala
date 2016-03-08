package org.template.classification

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

class Query(
  val user: String,
  val testGroupId: String
) extends Serializable

class PredictedResult(
  val category: String
) extends Serializable

class ActualResult(
  val category: String
) extends Serializable

object ClassificationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("PageVariantRecommender" -> classOf[VowpalPageVariantRecommenderAlgorithm]),
      classOf[Serving])
  }
}
