package ml.combust.pachyderm.mleap.demo.scoring

import java.io.File

import com.typesafe.config.Config
import ml.combust.bundle.BundleFile
import ml.combust.mleap.runtime.DefaultLeapFrame
import ml.combust.mleap.runtime.serialization.{BuiltinFormats, FrameReader}
import ml.combust.mleap.runtime.MleapSupport._

import scala.collection.JavaConverters._
import resource._

import scala.util.Try

/**
  * Created by hollinwilkins on 2/16/17.
  */
class Score {
  def execute(config: Config): Unit = {
    val modelPath = config.getString("model")
    val inputPath = config.getString("input")
    val outputPath = config.getString("output")
    val outputCols = if(config.hasPath("output-columns")) {
      Some(config.getStringList("output-columns").asScala)
    } else { None }

    (for(bf <- managed(BundleFile(new File(modelPath)))) yield {
      for(transformer <- bf.loadMleapBundle().map(_.root);
          frame <- FrameReader(BuiltinFormats.avro).read(new File(inputPath));
          frame2 <- transformer.transform(frame);
          frame3 <- selected(frame2, outputCols)) yield {
        frame3.writer(BuiltinFormats.avro).save(new File(outputPath))
      }.get
    }).tried.get
  }

  private def selected(frame: DefaultLeapFrame, cols: Option[Seq[String]]): Try[DefaultLeapFrame] = {
    cols.map {
      c => frame.select(c: _*)
    }.getOrElse(Try(frame))
  }
}
