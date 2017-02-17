package ml.combust.pachyderm.mleap.demo.scoring

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._

/**
  * Created by hollinwilkins on 2/16/17.
  */
object Boot extends App {
  val parser = new scopt.OptionParser[Config]("pmd-scoring") {
    head("pmd-scoring", "0.1-SNAPSHOT")

    opt[String]('m', "model").required().text("path to MLeap model").action {
      (model, config) => config.withValue("model", fromAnyRef(model))
    }

    opt[String]('i', "input").required().text("input path").action {
      (input, config) => config.withValue("input", fromAnyRef(input))
    }

    opt[String]('o', "output").required().text("output path").action {
      (output, config) => config.withValue("output", fromAnyRef(output))
    }

    opt[Seq[String]]('c', "output-columns").text("columns to output").action {
      (cols, config) => config.withValue("output-columns", fromAnyRef(cols.asJava))
    }
  }

  parser.parse(args, ConfigFactory.empty()) match {
    case Some(config) =>
      new Score().execute(config)
    case None => // do nothing
  }
}
