import AssemblyKeys._

assemblySettings

name := "contextual-bandit"

version := "0.3.0"

organization := "com.actionml"

val pioVersion = "0.11.0-SNAPSHOT"

val sparkVersion = "1.6.3"

resolvers += Resolver.sonatypeRepo("snapshots")   

libraryDependencies ++= {
   val artifact = "vw-jni"
   val osName = if(sys.props("os.name")=="Mac OS X") "mac" else "Linux"
   Seq(
  "org.apache.predictionio"    %% "core"          % pioVersion % "provided",
  "org.apache.spark" %% "spark-core"    % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib"   % sparkVersion % "provided",
  "com.github.johnlangford" % artifact % "8.0.0")
}
