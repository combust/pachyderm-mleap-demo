package ml.combust.pachyderm.mleap.demo.training

import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession

/**
  * Created by hollinwilkins on 2/16/17.
  */
trait Trainer {
  def train(config: Config): Unit = {
    val spark = SparkSession.builder().
      appName("Pachyderm MLeap Demo").
      master("local[1]").
      getOrCreate()

    sparkTrain(spark, config)

    spark.close()
  }

  def sparkTrain(spark: SparkSession, config: Config): Unit
}
