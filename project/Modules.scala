package ml.combust.pachyderm.mleap.demo

import sbt._

object Modules {
  lazy val aggregateProjects: Seq[ProjectReference] = Seq(
    `training`,
    `scoring`
  )

  lazy val `root` = Project(
    id = "root",
    base = file("."),
    aggregate = aggregateProjects
  )

  lazy val `training` = Project(
    id = "training",
    base = file("training")
  )

  lazy val `scoring` = Project(
    id = "scoring",
    base = file("scoring")
  )
}