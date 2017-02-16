import ml.combust.pachyderm.mleap.demo.{Common, Dependencies, DockerConfig}

enablePlugins(DockerPlugin, JavaAppPackaging)

Common.buildSettings
Dependencies.training
DockerConfig.trainingSettings
