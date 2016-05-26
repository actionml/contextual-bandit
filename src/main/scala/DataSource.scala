package org.template.classification

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.Event
import io.prediction.data.store.PEventStore
import io.prediction.data.storage.DataMap
import io.prediction.data.storage.PropertyMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import io.prediction.core.{EventWindow, SelfCleaningDataSource}

import grizzled.slf4j.Logger

case class DataSourceParams(
  appName: String,
  eventWindow: Option[EventWindow]
) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, ActualResult] with SelfCleaningDataSource {

  @transient override lazy val logger = Logger[this.type]

  override def appName = dsp.appName
  override def eventWindow = dsp.eventWindow

  override
  def readTraining(sc: SparkContext): TrainingData = {

    cleanPersistedPEvents(sc)

    val testGroupsPropsRDD = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "testGroup")(sc)   


    val testGroupsRDD = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("testGroup"))(sc)

    println(testGroupsRDD.count)

    val usersRDD = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "user")(sc)


    println(usersRDD.count)

    val eventsRDD = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      targetEntityType = Some(Some("variant")))(sc)

    val examples: RDD[VisitorVariantExample] = eventsRDD.map{ event =>
        new VisitorVariantExample(event.properties.get[Boolean]("converted"), event.entityId, event.targetEntityId.getOrElse(throw new RuntimeException), event.properties.get[String]("testGroupId"), event.properties -- List("converted", "testGroupId"))
      }.cache()

    val testPeriodStartTimes = testGroupsRDD.map( testGroup => testGroup.entityId -> testGroup.properties.get[String]("testPeriodStart"))

    new TrainingData(examples, usersRDD, testGroupsPropsRDD)
  }

  //TODO: remove
  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
      ???
    }
  
}

class VisitorVariantExample ( val converted: Boolean, val user: String, val variant: String, val testGroupId: String, val props: DataMap ) extends Serializable

class TrainingData(
  val trainingExamples: RDD[VisitorVariantExample],
  val users: RDD[(String, PropertyMap)],
  val testGroups: RDD[(String, PropertyMap)]
) extends Serializable
