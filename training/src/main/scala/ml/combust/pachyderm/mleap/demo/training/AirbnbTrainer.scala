package ml.combust.pachyderm.mleap.demo.training

import java.io.File

import com.typesafe.config.Config
import org.apache.spark.sql.{DataFrame, SparkSession}
import com.databricks.spark.avro._
import ml.combust.bundle.BundleFile
import org.apache.spark.ml.bundle.SparkBundleContext
import org.apache.spark.ml.{Pipeline, PipelineModel, PipelineStage}
import org.apache.spark.ml.feature.{OneHotEncoder, StandardScaler, StringIndexer, VectorAssembler}
import org.apache.spark.ml.regression.{LinearRegression, RandomForestRegressor}
import ml.combust.mleap.spark.SparkSupport._

import resource._

/**
  * Created by hollinwilkins on 2/16/17.
  */
class AirbnbTrainer extends Trainer {
  val continuousFeatures = Array("bathrooms",
    "bedrooms",
    "security_deposit",
    "cleaning_fee",
    "extra_people",
    "number_of_reviews",
    "square_feet",
    "review_scores_rating")

  val categoricalFeatures = Array("room_type",
    "host_is_superhost",
    "cancellation_policy",
    "instant_bookable",
    "state")

  val allFeatures = continuousFeatures.union(categoricalFeatures)

  override def sparkTrain(spark: SparkSession, config: Config): Unit = {
    val inputPath = config.getString("input")
    val outputPath = config.getString("output")

    val dataset = spark.sqlContext.read.avro(inputPath)

    // Step 2. Create our feature pipeline and train it on the entire dataset

    // Filter all null values
    val allCols = allFeatures.union(Seq("price")).map(dataset.col)
    val nullFilter = allCols.map(_.isNotNull).reduce(_ && _)
    val datasetFiltered = dataset.select(allCols: _*).filter(nullFilter).persist()

    val Array(trainingDataset, validationDataset) = datasetFiltered.randomSplit(Array(0.7, 0.3))
    val sparkPipelineModel = config.getString("type") match {
      case "linear-regression" =>
        val featurePipeline = createFeaturePipelineLr(dataset)
        createPipelineLr(trainingDataset, featurePipeline)
      case "random-forest" =>
        val featurePipeline = createFeaturePipelineRf(dataset)
        createPipelineRf(trainingDataset, featurePipeline)
    }

    val sbc = SparkBundleContext().withDataset(sparkPipelineModel.transform(dataset))
    (for(bf <- managed(BundleFile(new File(outputPath)))) yield {
      sparkPipelineModel.writeBundle.save(bf)(sbc).get
    }).tried.get
  }

  private def createPipelineLr(dataset: DataFrame, featurePipeline: PipelineModel): PipelineModel = {
    val linearRegression = new LinearRegression(uid = "linear_regression").
      setFeaturesCol("features").
      setLabelCol("price").
      setPredictionCol("price_prediction")

    val sparkPipelineEstimatorLr = new Pipeline().setStages(Array(featurePipeline, linearRegression))
    sparkPipelineEstimatorLr.fit(dataset)
  }

  private def createPipelineRf(dataset: DataFrame, featurePipeline: PipelineModel): PipelineModel = {
    val randomForest = new RandomForestRegressor(uid = "random_forest_regression").
      setFeaturesCol("features").
      setLabelCol("price").
      setPredictionCol("price_prediction")

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
    dataset.printSchema()

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

//    val featureCols = categoricalFeatureIndexers.map(_.getOutputCol).union(Seq("scaled_continuous_features"))

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
