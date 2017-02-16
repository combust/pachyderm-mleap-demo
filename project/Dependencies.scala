package ml.combust.pachyderm.mleap.demo

import sbt._
import Keys._

object Dependencies {
  val sparkVersion = "2.1.0"
  val mleapVersion = "0.6.0-SNAPSHOT"

  object Compile {
    val spark = Seq("org.apache.spark" %% "spark-core" % sparkVersion,
      "org.apache.spark" %% "spark-sql" % sparkVersion,
      "org.apache.spark" %% "spark-mllib" % sparkVersion,
      "org.apache.spark" %% "spark-mllib-local" % sparkVersion,
      "org.apache.spark" %% "spark-catalyst" % sparkVersion)
    val mleapSpark = "ml.combust.mleap" %% "mleap-spark" % mleapVersion
    val mleapRuntime = "ml.combust.mleap" %% "mleap-runtime" % mleapVersion
    val mleapAvro = "ml.combust.mleap" %% "mleap-avro" % mleapVersion
    val sparkAvro = "com.databricks" %% "spark-avro" % "3.2.0"
    val scopt = "com.github.scopt" %% "scopt" % "3.5.0"
    val config = "com.typesafe" % "config" % "1.3.0"
  }

  import Compile._
  val l = libraryDependencies

  val training = l ++= Seq(mleapSpark, sparkAvro, scopt, config) ++ spark
  val scoring = l ++= Seq(mleapRuntime, mleapAvro, scopt, config)
}