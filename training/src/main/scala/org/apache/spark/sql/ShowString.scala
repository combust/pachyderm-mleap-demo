package org.apache.spark.sql

/**
  * Created by hollinwilkins on 2/16/17.
  */
object ShowString {
  def showString(dataset: DataFrame, n: Int = 20, truncate: Int = 20): String = {
    dataset.showString(n, truncate)
  }
}
