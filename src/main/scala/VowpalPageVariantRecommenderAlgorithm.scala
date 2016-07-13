package org.template.classification

import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.PropertyMap
import io.prediction.data.store.LEventStore
import io.prediction.data.storage.Event

import org.joda.time.Duration
import org.joda.time.DateTime
import org.joda.time.Minutes
import org.json4s._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vector
import grizzled.slf4j.Logger

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
  maxClasses: Int
) extends Params

case class PageVariantModel(
  model: Array[Byte],
  userData: Map[String, PropertyMap],
  classes: Map[String, Seq[(Int,String)]],
  testPeriodStarts: Map[String, String],
  testPeriodEnds: Map[String, String]
)

// extends P2LAlgorithm because VW doesn't contain RDD.
class VowpalPageVariantRecommenderAlgorithm(val ap: AlgorithmParams)
  extends P2LAlgorithm[PreparedData, PageVariantModel, Query, PredictedResult] { 

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): PageVariantModel = {
   
    //require(!data.examples.take(1).isEmpty,
    //  s"No page variants events found, please insert page variant events")

    

    //require(!data.users.take(1).isEmpty,
    //  s"No users found, please initialize users")

    require(!data.testGroups.take(1).isEmpty,
      s"No test groups found, please initialize test groups") 


    @transient implicit lazy val formats = org.json4s.DefaultFormats

    val collectedTestGroups = data.testGroups.collect()

    val testPeriodEnds = collectedTestGroups.map( x => x._1 -> x._2.fields("testPeriodEnd").extract[String]).toMap    

    val testPeriodStarts = collectedTestGroups.map( x => x._1 -> x._2.fields("testPeriodStart").extract[String]).toMap

    val classes = collectedTestGroups.map( x => (x._1, (1 to ap.maxClasses) zip x._2.fields("pageVariants").extract[List[String]])).toMap 

    val userData = data.users.collect().map( x => x._1 -> x._2).toMap

    val inputs: RDD[String] = data.examples.map { example =>
      val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())
     
      //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it 
      val classString: String = testGroupClasses.map { thisClass => thisClass._1.toString + ":" + 
         (if(thisClass._2 == example.variant && example.converted) "0.0" else if(thisClass._2 == example.variant) "2.0" else "1.0") }.mkString(" ")
  
    constructVWString(classString, example.user, example.testGroupId, userData) 
    }
        
  
    val reg = "--l2 " + ap.regParam
    val iters = "-c -k --passes " + ap.maxIter
    val lrate = "-l " + ap.stepSize

    //ap.maxClasses 
 
    val vw = new VW("--csoaa 10 " + "-b " + ap.bitPrecision + " " + "-f " + ap.modelName + " " + reg + " " + lrate + " " + iters)
        
    for (item <- inputs.collect()) println(item)

    val results = for (item <- inputs.collect()) yield vw.learn(item)  
   

    vw.close()

    PageVariantModel(Files.readAllBytes(Paths.get(ap.modelName)), userData, classes, testPeriodStarts, testPeriodEnds) 
  }
 
  def predict(model: PageVariantModel, query: Query): PredictedResult = {
   
    println(model.classes)

    val pageVariant = if(model.classes isDefinedAt query.testGroupId) getPageVariant(model, query) else getDefaultPageVariant(query)

    //for (item <- probabilityMap) println(item)
  
    val result = new PredictedResult(pageVariant, query.testGroupId)
   
    result
  }


  def getPageVariant(model: PageVariantModel, query: Query): String = {
     val vw = new VW(" -i " + ap.modelName)

    val numClasses = model.classes(query.testGroupId).size


    val classString = (1 to numClasses).mkString(" ")

    val queryText = constructVWString(classString, query.user, query.testGroupId, model.userData)

    val pred = vw.predict(queryText).toInt
    vw.close()

    //see http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.109.4518&rep=rep1&type=pdf
    //we use testPeriods as epsilon0

    val startTime = new DateTime(model.testPeriodStarts(query.testGroupId))
    val endTime =  new DateTime(model.testPeriodEnds(query.testGroupId))

    val maxEpsilon = 1.0 - (1.0/numClasses)
    val currentTestDuration = new Duration(startTime, new DateTime()).getStandardMinutes().toDouble
    val totalTestDuration = new Duration(startTime, endTime).getStandardMinutes().toDouble

    //scale epsilonT to the range 0.0-maxEpsilon
    val epsilonT = scala.math.max(0, scala.math.min(maxEpsilon, maxEpsilon * (1.0 - currentTestDuration/ totalTestDuration) ))

    val testGroupMap = model.classes(query.testGroupId).toMap

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

  def constructVWString(classString: String, user: String, testGroupId: String, userProps: Map[String,PropertyMap]): String = {
      @transient implicit lazy val formats = org.json4s.DefaultFormats

     classString + " |" +  ap.namespace + " " + rawTextToVWFormattedString("user_" + user + " " + "testGroupId_" + testGroupId + " " + (userProps.getOrElse(user, PropertyMap(Map[String,JValue](), new DateTime(), new DateTime())) -- List("converted", "testGroupId")).fields.map { entry =>
          entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId }.mkString(" "))
}

}
