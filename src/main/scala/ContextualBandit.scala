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

import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.PropertyMap
import org.apache.predictionio.data.store.LEventStore
import org.apache.predictionio.data.storage.Event

import org.joda.time.Duration
import org.joda.time.DateTime
import org.joda.time.Minutes
import org.json4s._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vector
import grizzled.slf4j.Logger

import scala.concurrent.duration._
import scala.concurrent.Await
import java.io.{ObjectOutputStream, FileOutputStream, ObjectInputStream, FileInputStream}
import java.nio.file.{Files, Paths}

import vw.VW

case class AlgorithmParams(
    appName: String,
    maxIter: Int,
    regParam: Double,
    stepSize: Double,
    bitPrecision: Int,
    modelName: String,
    namespace: String,
    maxClasses: Int,
    initialize: Boolean) extends Params

case class ContextualBanditModel(
    model: Array[Byte],
    userData: UserData,
    classes: Classes,
    testPeriodStarts: TestPeriodStarts,
    testPeriodEnds: TestPeriodEnds )

case class UserData(
    data: Map[String,PropertyMap])

case class Classes(
    classes: Map[String, Seq[(Int,String)]])

case class TestPeriodStarts(
    testPeriodStarts: Map[String, String])

case class TestPeriodEnds(
    testPeriodEnds: Map[String, String])


// extends P2LAlgorithm because VW doesn't contain RDD.
class ContextualBandit(val ap: AlgorithmParams)
  extends P2LAlgorithm[PreparedData, ContextualBanditModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  @transient implicit lazy val formats = org.json4s.DefaultFormats

  val ds = new DataSource(DataSourceParams(ap.appName, None))

  def train(sc: SparkContext, data: PreparedData): ContextualBanditModel = {

  if(!ap.initialize){
     while(true){
       val vw = createVW()

       val freshData = ds.readTraining(sc)
       val freshPreparedData = new PreparedData(freshData.trainingExamples, freshData.users, freshData.testGroups)

      require(!freshPreparedData.testGroups.take(1).isEmpty,
        s"No test groups found, please initialize test groups")

      val (classes, testPeriodStarts, testPeriodEnds) = testGroupToClassesAndPeriodBounds(freshPreparedData)

      val userData = freshPreparedData.users.collect().map( x => x._1 -> x._2).toMap

      trainOnAllHistoricalData(freshPreparedData, classes, userData,vw)

      vw.close()

      saveObject(UserData(userData), "userData")
      saveObject(Classes(classes), "classes")
      saveObject(TestPeriodStarts(testPeriodStarts), "testPeriodStarts")
      saveObject(TestPeriodEnds(testPeriodEnds), "testPeriodEnds")
    }
  }
    return ContextualBanditModel(null, null, null, null, null)
  }

  private def saveObject(obj: Serializable, name: String){
    val stream = new ObjectOutputStream(new FileOutputStream(name))
    stream.writeObject(obj)
    stream.close
  }

  private def readObject[T](name: String): T = {
    val stream = new ObjectInputStream(new FileInputStream(name))
    val obj = stream.readObject.asInstanceOf[T]
    stream.close
    return obj
  }

  private def createVW() : VW = {
    val reg = "--l2 " + ap.regParam
    val iters = "-c -k --passes " + ap.maxIter
    val lrate = "-l " + ap.stepSize

    val vw = new VW("--csoaa 10 " + "-b " + ap.bitPrecision + " " + "-f " + ap.modelName + " " + reg + " " + lrate + " " + iters)
    return vw
  }

  private def trainOnAllHistoricalData(data: PreparedData, classes: Map[String,Seq[(Int, String)]], userData: Map[String, PropertyMap], vw: VW) {

    val inputs: Seq[String] = examplesToVWStrings(data, classes, userData)

    // for (item <- inputs) println(item)

    val results = for (item <- inputs) yield vw.learn(item)
  }

  private def testGroupToClassesAndPeriodBounds(data:PreparedData): Tuple3[Map[String,Seq[(Int, String)]], Map[String,String], Map[String,String]] = {
    val collectedTestGroups = data.testGroups.collect()

    val testPeriodEnds = collectedTestGroups.map( x => x._1 -> x._2.fields("testPeriodEnd").extract[String]).toMap

    val testPeriodStarts = collectedTestGroups.map( x => x._1 -> x._2.fields("testPeriodStart").extract[String]).toMap

    val classes = collectedTestGroups.map( x => (x._1, (1 to ap.maxClasses) zip x._2.fields("pageVariants").extract[List[String]])).toMap

    return (classes, testPeriodStarts, testPeriodEnds)
  }


  private def examplesToVWStrings(data: PreparedData, classes: Map[String,Seq[(Int, String)]], userData: Map[String, PropertyMap]): Seq[String] = {
      val inputs: Seq[String] = data.examples.collect.map { example =>
      val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())

      //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it
      val classString: String = testGroupClasses.map { thisClass => thisClass._1.toString + ":" +
         (if(thisClass._2 == example.variant && example.converted) "0.0" else if(thisClass._2 == example.variant) "2.0" else "1.0") }.mkString(" ")

    constructVWString(classString, example.user, example.testGroupId, userData)
    }

    return inputs
  }


  def predict(origModel: ContextualBanditModel, query: Query): PredictedResult = {

    val newModel = ContextualBanditModel(origModel.model,
                                    readObject[UserData]("userData"),
                                    readObject[Classes]("classes"),
                                    readObject[TestPeriodStarts]("testPeriodStarts"),
                                    readObject[TestPeriodEnds]("testPeriodEnds")
                                   )

    //println(model.classes)
    //println(model.userData)

    val pageVariant = if(newModel.classes.classes isDefinedAt query.testGroupId) getPageVariant(newModel, query) else getDefaultPageVariant(query)

    //for (item <- probabilityMap) println(item)

    val result = new PredictedResult(pageVariant, query.testGroupId)

    result
  }


  def getPageVariant(model: ContextualBanditModel, query: Query): String = {
     val vw = new VW(" -i " + ap.modelName)

    val numClasses = model.classes.classes(query.testGroupId).size


    val classString = (1 to numClasses).mkString(" ")

    val queryText = constructVWString(classString, query.user, query.testGroupId, model.userData.data)

    val pred = vw.predict(queryText).toInt
    vw.close()

    //see http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.109.4518&rep=rep1&type=pdf
    //we use testPeriods as epsilon0

    val startTime = new DateTime(model.testPeriodStarts.testPeriodStarts(query.testGroupId))
    val endTime =  new DateTime(model.testPeriodEnds.testPeriodEnds(query.testGroupId))

    val maxEpsilon = 1.0 - (1.0/numClasses)
    val currentTestDuration = new Duration(startTime, new DateTime()).getStandardMinutes().toDouble
    val totalTestDuration = new Duration(startTime, endTime).getStandardMinutes().toDouble

    //scale epsilonT to the range 0.0-maxEpsilon
    val epsilonT = scala.math.max(0, scala.math.min(maxEpsilon, maxEpsilon * (1.0 - currentTestDuration/ totalTestDuration) ))

    val testGroupMap = model.classes.classes(query.testGroupId).toMap

    val probabilityMap = testGroupMap.keys.map { x => x -> (if(x == pred) 1.0 - epsilonT else epsilonT/ (numClasses - 1.0) ) }.toMap 

    val sampledPred = sample(probabilityMap)

    val pageVariant = testGroupMap(sampledPred)
    return pageVariant 
  }

  def getDefaultPageVariant(query: Query): String = {
     logger.info("Test group has not been trained with  yet. Fall back to uniform distribution")
     val testGroupEvent: Event = LEventStore.findByEntity(
        appName = ap.appName,
        // entityType and entityId is specified for fast lookup
        entityType = "testGroup",
        entityId = query.testGroupId,
        latest = true,
        // set time limit to avoid super long DB access
        timeout = scala.concurrent.duration.Duration(200, "millis")
     ).toList.head
   
     val props = testGroupEvent.properties
     val pageVariants = props.get[Array[String]]("pageVariants")
     val numClasses = pageVariants.size
     val map = pageVariants.map{ x => x -> 1.0/numClasses}.toMap 
     val sampledPred = sample(map)
     sampledPred
  }

  def sample[A](dist: Map[A, Double]): A = {
    val p = scala.util.Random.nextDouble

    val rangedProbs = dist.values.scanLeft(0.0)(_ + _).drop(1)

    val rangedMap = (dist.keys zip rangedProbs).toMap

    val item = dist.filter( x => rangedMap(x._1) >= p).keys.head

    item
  }

  def rawTextToVWFormattedString(str: String) : String = {
     //VW input cannot contain these characters 
     str.replaceAll("[|:]", "")
  }

  def vectorToVWFormattedString(vec: Vector): String = {
     vec.toArray.zipWithIndex.map{ case (dbl, int) => s"$int:$dbl"} mkString " "
  }

  def constructVWString(classString: String, user: String, testGroupId: String, userProps: Map[String,PropertyMap]):
    String = {
    @transient implicit lazy val formats = org.json4s.DefaultFormats

    classString + " |" +  ap.namespace + " " + rawTextToVWFormattedString("user_" + user + " " + "testGroupId_" +
      testGroupId + " " + (userProps.getOrElse(user, PropertyMap(Map[String,JValue](), new DateTime(),
      new DateTime())) --
      List("converted", "testGroupId")).fields.map { entry =>
          entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId
      }.mkString(" "))
  }

}
