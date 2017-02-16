package ml.combust.pachyderm.mleap.demo

import com.typesafe.sbt.SbtNativePackager.autoImport.packageName
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.docker.ExecCmd
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

object DockerConfig {
  val baseSettings = Seq(daemonUser in Docker := "root",
    dockerExposedPorts := Seq(65327),
    dockerRepository := Some("combustml"),
    dockerBuildOptions := Seq("-t", dockerAlias.value.versioned) ++ (
      if (dockerUpdateLatest.value)
        Seq("-t", dockerAlias.value.latest)
      else
        Seq()
      ),
    dockerCommands := dockerCommands.value.filterNot {
      case ExecCmd("RUN", args @ _*) => args.contains("chown")
      case cmd => false
    })

  val trainingSettings = Seq(packageName := "pmd-training") ++ baseSettings
  val scoringSettings = Seq(packageName := "pmd-scoring") ++ baseSettings
}