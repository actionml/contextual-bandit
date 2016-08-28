import AssemblyKeys._

assemblySettings

name := "template-scala-page-variant-recommender"

organization := "io.prediction"

resolvers += Resolver.sonatypeRepo("snapshots")   

libraryDependencies ++= {
   val artifact = "vw-jni"
   val osName = if(sys.props("os.name")=="Mac OS X") "mac" else "Linux"
   Seq(
  "org.apache.predictionio"    %% "core"          % "0.10.0-SNAPSHOT" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.3.0" % "provided",
  "org.apache.spark" %% "spark-mllib"   % "1.3.0" % "provided",
  "com.github.johnlangford" % artifact % "8.0.0")
}
