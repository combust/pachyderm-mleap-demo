package ml.combust.pachyderm.mleap.demo.training

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef

/**
  * Created by hollinwilkins on 2/16/17.
  */
object Boot extends App {
  val parser = new scopt.OptionParser[Config]("pmd-training") {
    head("pmd-training", "0.1.0")

    def input = opt[String]('i', "input").required().text("path to input directory").action {
      (path, config) => config.withValue("input", fromAnyRef(path))
    }

    def output = opt[String]('o', "output").required().text("path to output MLeap bundle").action {
      (path, config) => config.withValue("output", fromAnyRef(path))
    }

    def validation = opt[String]('l', "validation").text("path to output validation dataset").action {
      (path, config) => config.withValue("validation", fromAnyRef(path))
    }

    def tpe = opt[String]('t', "type").required().text("model type to train: linear-regression or random-forest").action {
      (tpe, config) => config.withValue("type", fromAnyRef(tpe))
    }

    def evaluation = opt[String]('s', "summary").text("optional path to model training summary").action {
      (summary, config) => config.withValue("summary", fromAnyRef(summary))
    }

    cmd("airbnb").text("train airbnb pricing model").action {
      (_, config) => config.withValue("trainer", fromAnyRef("ml.combust.pachyderm.mleap.demo.training.AirbnbTrainer"))
    }.children(input, output, validation, tpe, evaluation)
  }

  parser.parse(args, ConfigFactory.empty()) match {
    case Some(config) =>
      Class.forName(config.getString("trainer")).
        newInstance().
        asInstanceOf[Trainer].
        train(config)
    case None => sys.exit(1)
  }
}
