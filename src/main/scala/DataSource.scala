/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.template.classification

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.store.PEventStore
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.PropertyMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.predictionio.core.{EventWindow, SelfCleaningDataSource}

import grizzled.slf4j.Logger

case class DataSourceParams(
  appName: String,
  eventWindow: Option[EventWindow]
) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, ActualResult]
  with SelfCleaningDataSource {

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
