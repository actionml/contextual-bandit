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

// extends P2LAlgorithm because VW doesn't contain RDD.
class VowpalPageVariantRecommenderAlgorithm(val ap: AlgorithmParams)
  extends P2LAlgorithm[PreparedData, Array[Byte], Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): Array[Byte] = {
   
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
    


    val classes = (1 to ap.maxClasses)

    val classes2 = data.testGroups.collect().map( x => (x._1, (1 to ap.maxClasses) zip x._2.fields("pageVariants").extract[List[String]])).toMap 

    val userData = data.users.collect().map( x => x._1 -> x._2).toMap

    val inputs: RDD[String] = data.examples.map { example =>
      val testGroupClasses = classes2.getOrElse(example.testGroupId, Seq[(Int, String)]())
      
      val classString: String = testGroupClasses.map { thisClass => thisClass._1.toString + ":" + 
         (if(thisClass._2 == example.variant && example.converted) "0.0" else if(thisClass._2 == example.variant) "2.0" else "1.0") }.mkString(" ")
 
  
 
    constructVWString(classString, example, userData.getOrElse(example.visitor, PropertyMap(Map[String,JValue](), new DateTime(), new DateTime())))

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
     
    Files.readAllBytes(Paths.get(ap.modelName))
  }


  //TODO: Same pre-processing as above
  //TODO: Reverse lookup of predicted class names
  //TODO: Sampling
 
  def predict(byteArray: Array[Byte], query: Query): PredictedResult = {
    Files.write(Paths.get(ap.modelName), byteArray)

    val vw = new VW("--link logistic -i " + ap.modelName)
    val pred = vw.predict("|" + ap.namespace + " " + rawTextToVWFormattedString(query.text)).toDouble 
    vw.close()

    val category = (if(pred > 0.5) 1 else 0).toString
    val prob = (if(pred > 0.5) pred else 1.0 - pred)
    val result = new PredictedResult(category, prob)
   
    result
  }

  def rawTextToVWFormattedString(str: String) : String = {
     //VW input cannot contain these characters 
     str.replaceAll("[|:]", "")
  }

  def vectorToVWFormattedString(vec: Vector): String = {
     vec.toArray.zipWithIndex.map{ case (dbl, int) => s"$int:$dbl"} mkString " "
  }

  def constructVWString(classString: String, example: VisitorVariantExample, userProps: PropertyMap): String = {
      @transient implicit lazy val formats = org.json4s.DefaultFormats

     classString + " |" +  ap.namespace + " " + rawTextToVWFormattedString("visitor_" + example.visitor + " " + "testGroupId_" + example.testGroupId + " " + (userProps -- List("converted", "testGroupId")).fields.map { entry =>
          entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + example.testGroupId }.mkString(" "))
}

}
