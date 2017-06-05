package ml.combust.pachyderm.mleap.demo.training
import java.io.File
import java.nio.file.{FileSystems, Files}

import com.typesafe.config.Config
import org.apache.spark.sql.{DataFrame, ShowString, SparkSession}
import com.databricks.spark.avro._
import ml.combust.bundle.BundleFile
import ml.combust.bundle.serializer.SerializationFormat
import ml.combust.bundle.util.FileUtil
import org.apache.spark.ml.bundle.SparkBundleContext
import org.apache.spark.ml.classification.{LogisticRegression, RandomForestClassifier}
import org.apache.spark.ml.{Pipeline, PipelineModel, PipelineStage}
import org.apache.spark.ml.feature._
import ml.combust.mleap.spark.SparkSupport._
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import resource._

import scala.collection.JavaConverters._

/**
  * Created by hollinwilkins on 5/24/17.
  */
class LendingClubTrainer extends Trainer {
  val continuousFeatures = Array("loan_amount",
    "dti")

  val categoricalFeatures = Array("loan_title",
    "emp_length",
    "state",
    "fico_score_group_fnl")

  val allFeatures = continuousFeatures.union(categoricalFeatures)

  override def sparkTrain(spark: SparkSession, config: Config): Unit = {
    val inputPath = config.getString("input")
    val outputPath = config.getString("output")

    assert(outputPath.endsWith(".zip"), "must output a zip MLeap bundle file")

    val dataset = spark.sqlContext.read.avro(inputPath)

    // Step 2. Create our feature pipeline and train it on the entire dataset

    // Filter all null values
    val allCols = allFeatures.union(Seq("approved")).map(dataset.col)
    val nullFilter = allCols.map(_.isNotNull).reduce(_ && _)
    val datasetFiltered = dataset.select(allCols: _*).filter(nullFilter).persist()

    val Array(trainingDataset, validationDataset) = datasetFiltered.randomSplit(Array(0.9, 0.1))
    val sparkPipelineModel = config.getString("type") match {
      case "logistic-regression" =>
        val featurePipeline = createFeaturePipelineLr(dataset)
        createPipelineLr(trainingDataset, featurePipeline)
      case "random-forest" =>
        val featurePipeline = createFeaturePipelineRf(dataset)
        createPipelineRf(trainingDataset, featurePipeline)
    }

    val modelFile = new File(outputPath)
    if(modelFile.exists()) { modelFile.delete() }
    val sbc = SparkBundleContext().withDataset(sparkPipelineModel.transform(dataset))
    (for(bf <- managed(BundleFile(modelFile))) yield {
      sparkPipelineModel.writeBundle.format(SerializationFormat.Json).save(bf)(sbc).get
    }).tried.get

    if(config.hasPath("validation")) {
      val validationFile = new File(config.getString("validation"))
      val validationFileTmp = new File(validationFile.toString + ".tmp")
      if(validationFile.exists()) { validationFile.delete() }
      if(validationFileTmp.exists()) { FileUtil.rmRf(validationFileTmp.toPath) }

      validationDataset.coalesce(1).write.avro(validationFileTmp.toString)
      val avroFile = validationFileTmp.listFiles().filter(_.toString.endsWith(".avro")).head
      Files.copy(avroFile.toPath, validationFile.toPath)
      FileUtil.rmRf(validationFileTmp.toPath)
    }

    if(config.hasPath("summary")) {
      val summaryPath = config.getString("summary")
      val strs = Seq(evaluationString(validationDataset, config.getString("type"), sparkPipelineModel)).asJava
      val file = FileSystems.getDefault.getPath(summaryPath)
      Files.deleteIfExists(file)
      Files.write(file, strs)
    }
  }

  private def evaluationString(dataset: DataFrame, modelType: String, model: PipelineModel): String = {
    val sb = new StringBuilder()

    val evalDataset = model.transform(dataset)
    val evaluator = new BinaryClassificationEvaluator().
      setLabelCol("approved").
      setRawPredictionCol("approved_raw_prediction").
      setMetricName("areaUnderROC")
    val areaUnderROC = evaluator.evaluate(evalDataset)

    sb.append(s"Trained Model: $modelType")
    sb.append("\n\n")
    sb.append(s"Area Under ROC: $areaUnderROC")

    sb.toString
  }

  private def createPipelineLr(dataset: DataFrame, featurePipeline: PipelineModel): PipelineModel = {
    val linearRegression = new LogisticRegression(uid = "logistic_regression").
      setFeaturesCol("features").
      setLabelCol("approved").
      setRawPredictionCol("approved_raw_prediction").
      setPredictionCol("approved_prediction")

    val sparkPipelineEstimatorLr = new Pipeline().setStages(Array(featurePipeline, linearRegression))
    sparkPipelineEstimatorLr.fit(dataset)
  }

  private def createPipelineRf(dataset: DataFrame, featurePipeline: PipelineModel): PipelineModel = {
    val randomForest = new RandomForestClassifier(uid = "random_forest_classifier").
      setMaxBins(256).
      setFeaturesCol("features").
      setLabelCol("approved").
      setRawPredictionCol("approved_raw_prediction").
      setPredictionCol("approved_prediction")

    val sparkPipelineEstimatorRf = new Pipeline().setStages(Array(featurePipeline, randomForest))
    sparkPipelineEstimatorRf.fit(dataset)
  }

  private def createFeaturePipelineLr(dataset: DataFrame): PipelineModel = {
    val continuousFeatureAssembler = new VectorAssembler(uid = "continuous_feature_assembler").
      setInputCols(continuousFeatures).
      setOutputCol("unscaled_continuous_features")

    val continuousFeatureScaler = new StandardScaler(uid = "continuous_feature_scaler").
      setInputCol("unscaled_continuous_features").
      setOutputCol("scaled_continuous_features")


    val categoricalFeatureIndexers = categoricalFeatures.map {
      feature => new StringIndexer(uid = s"string_indexer_$feature").
        setInputCol(feature).
        setOutputCol(s"${feature}_index")
    }
    val categoricalFeatureOneHotEncoders = categoricalFeatureIndexers.map {
      indexer => new OneHotEncoder(uid = s"oh_encoder_${indexer.getOutputCol}").
        setInputCol(indexer.getOutputCol).
        setOutputCol(s"${indexer.getOutputCol}_oh")
    }

    val featureCols = categoricalFeatureOneHotEncoders.map(_.getOutputCol).union(Seq("scaled_continuous_features"))

    // assemble all processes categorical and continuous features into a single feature vector
    val featureAssembler = new VectorAssembler(uid = "feature_assembler").
      setInputCols(featureCols).
      setOutputCol("features")

    val estimators: Array[PipelineStage] = Array(continuousFeatureAssembler, continuousFeatureScaler).
      union(categoricalFeatureIndexers).
      union(categoricalFeatureOneHotEncoders).
      union(Seq(featureAssembler))

    new Pipeline(uid = "feature_pipeline").
      setStages(estimators).
      fit(dataset)
  }

  private def createFeaturePipelineRf(dataset: DataFrame): PipelineModel = {
    val continuousFeatureAssembler = new VectorAssembler(uid = "continuous_feature_assembler").
      setInputCols(continuousFeatures).
      setOutputCol("unscaled_continuous_features")

    val continuousFeatureScaler = new StandardScaler(uid = "continuous_feature_scaler").
      setInputCol("unscaled_continuous_features").
      setOutputCol("scaled_continuous_features")

    val categoricalFeatureIndexers = categoricalFeatures.map {
      feature => new StringIndexer(uid = s"string_indexer_$feature").
        setInputCol(feature).
        setOutputCol(s"${feature}_index")
    }

    // assemble all processes categorical and continuous features into a single feature vector
    val featureAssembler = new VectorAssembler(uid = "feature_assembler").
      setInputCols(Array("scaled_continuous_features")).
      setOutputCol("features")

    val estimators: Array[PipelineStage] = Array(continuousFeatureAssembler, continuousFeatureScaler).
      union(categoricalFeatureIndexers).
      union(Seq(featureAssembler))

    new Pipeline(uid = "feature_pipeline").
      setStages(estimators).
      fit(dataset)
  }
}
