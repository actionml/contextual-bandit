package org.template.classification

import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.PropertyMap

import org.joda.time.DateTime
import org.json4s._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vector
import grizzled.slf4j.Logger


import java.nio.file.{Files, Paths}

import vw.VW

case class AlgorithmParams(
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
  userData: Map[String, PropertyMap]
)

// extends P2LAlgorithm because VW doesn't contain RDD.
class VowpalPageVariantRecommenderAlgorithm(val ap: AlgorithmParams)
  extends P2LAlgorithm[PreparedData, PageVariantModel, Query, PredictedResult] { 

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): PageVariantModel = {
   
    require(!data.examples.take(1).isEmpty,
      s"RDD[VisitorVariantExample] in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    

    require(!data.users.take(1).isEmpty,
      s"RDD[(String, PropertyMap)] in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")

    require(!data.testGroups.take(1).isEmpty,
      s"RDD[(String, PropertyMap)] in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")


    @transient implicit lazy val formats = org.json4s.DefaultFormats
    


    //val classes = (1 to ap.maxClasses)

    val classes = data.testGroups.collect().map( x => (x._1, (1 to ap.maxClasses) zip x._2.fields("pageVariants").extract[List[String]])).toMap 

    val userData = data.users.collect().map( x => x._1 -> x._2).toMap

    val inputs: RDD[String] = data.examples.map { example =>
      val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())
      
      val classString: String = testGroupClasses.map { thisClass => thisClass._1.toString + ":" + 
         (if(thisClass._2 == example.variant && example.converted) "0.0" else if(thisClass._2 == example.variant) "2.0" else "1.0") }.mkString(" ")
  
    constructVWString(classString, example.user, example.testGroupId, userData)

    //  }.union(List[String](sys.props("line.separator")))
    }
        
  
    val reg = "--l2 " + ap.regParam
    //val iters = "-c -k --passes " + ap.maxIter
    val lrate = "-l " + ap.stepSize

    //ap.maxClasses 
 
    val vw = new VW("--csoaa 10 " + "--invert_hash readable.model -b " + ap.bitPrecision + " " + "-f " + ap.modelName + " " + reg + " " + lrate)
        
    for (item <- inputs.collect()) println(item)

    val results = for (item <- inputs.collect()) yield vw.learn(item)  
   
    vw.close()
     
    PageVariantModel(Files.readAllBytes(Paths.get(ap.modelName)), userData) 
  }


  //TODO: Same pre-processing as above
  //TODO: Reverse lookup of predicted class names
  //TODO: Sampling
 
  def predict(model: PageVariantModel, query: Query): PredictedResult = {
    Files.write(Paths.get(ap.modelName), model.model)

    val vw = new VW(" -i " + ap.modelName)
   
    val classString = (1 to ap.maxClasses).mkString(" ")

    println(model.userData)
 
    val queryText = constructVWString(classString, query.user, query.testGroupId, model.userData)

    //val queryText = classString +  " |" + ap.namespace + " " + rawTextToVWFormattedString(query.user + " " + query.testGroupId)
    println(queryText)
    val pred = vw.predict(queryText).toDouble 
    vw.close()

    val pageVariant = pred.toString 
    val result = new PredictedResult(pageVariant)
   
    result
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
