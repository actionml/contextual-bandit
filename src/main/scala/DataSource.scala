package org.template.classification

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.Event
import io.prediction.data.store.PEventStore
import io.prediction.data.storage.DataMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors

import grizzled.slf4j.Logger

case class DataSourceParams(
  appName: String
) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, ActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {

  val eventsRDD = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("visitor"),
      targetEntityType = Some(Some("variant")))(sc)

      val examples: RDD[VisitorVariantExample] = eventsRDD.map{ event =>
        new VisitorVariantExample(event.properties.get[Boolean]("converted"), event.entityId, event.targetEntityId.getOrElse(throw new RuntimeException), event.properties.get[String]("testGroupId"), event.properties -- List("converted", "testGroupId"))
      }.cache()

    new TrainingData(examples)
  }

  //TODO: remove
  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
      ???
    }
  
}

class VisitorVariantExample ( val converted: Boolean, val visitor: String, val variant: String, val testGroupId: String, val props: DataMap ) extends Serializable

class TrainingData(
  val trainingExamples: RDD[VisitorVariantExample]
) extends Serializable
