package org.template.classification

import io.prediction.controller.PPreparator
import io.prediction.data.storage.PropertyMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


class PreparedData(
  val examples: RDD[VisitorVariantExample],
  val users: RDD[(String, PropertyMap)],
  val testGroups: RDD[(String, PropertyMap)],
  val testPeriodStarts: RDD[(String, String)]
) extends Serializable

class Preparator extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(trainingData.trainingExamples, trainingData.users, trainingData.testGroups, trainingData.testPeriodStarts)
  }
}
