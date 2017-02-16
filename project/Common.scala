package ml.combust.pachyderm.mleap.demo

import sbt._
import sbt.Keys._

object Common {
  lazy val buildSettings: Seq[Def.Setting[_]] = Seq(
    organization := "ml.combust.pachyderm",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.11.8"),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )
}