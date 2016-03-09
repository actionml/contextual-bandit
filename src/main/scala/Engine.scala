package org.template.classification

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

class Query(
  val user: String,
  val testGroupId: String
) extends Serializable

class PredictedResult(
  val variant: String,
  val testGroupId: String
) extends Serializable

class ActualResult(
  val variant: String
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
