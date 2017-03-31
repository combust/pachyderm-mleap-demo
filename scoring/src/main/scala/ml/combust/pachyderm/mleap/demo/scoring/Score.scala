package ml.combust.pachyderm.mleap.demo.scoring

import java.io.File

import com.typesafe.config.Config
import ml.combust.bundle.BundleFile
import ml.combust.mleap.runtime.DefaultLeapFrame
import ml.combust.mleap.runtime.serialization.{BuiltinFormats, FrameReader}
import ml.combust.mleap.runtime.MleapSupport._
import ml.combust.mleap.runtime.transformer.Transformer

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

    new File(outputPath).mkdirs()
    (for(bf <- managed(BundleFile(new File(modelPath)))) yield {
      (for(transformer <- bf.loadMleapBundle().map(_.root)) yield {
        new File(inputPath).list().filter(_.endsWith(".avro")).foreach {
          input =>
            val output = new File(outputPath, input)
            transformFile(transformer, new File(inputPath, input), output, outputCols)
        }
      }).get
    }).tried.get
  }

  def transformFile(transformer: Transformer, input: File, output: File, cols: Option[Seq[String]]): Unit = {
    println(s"Transforming: $input to $output")
    (for(frame <- FrameReader(BuiltinFormats.avro).read(input);
        frame2 <- transformer.transform(frame);
        frame3 <- selected(frame2, cols)) yield {
      frame3.writer(BuiltinFormats.avro).save(output).get
    }).get
  }

  private def selected(frame: DefaultLeapFrame, cols: Option[Seq[String]]): Try[DefaultLeapFrame] = {
    cols.map {
      c => frame.select(c: _*)
    }.getOrElse(Try(frame))
  }
}
