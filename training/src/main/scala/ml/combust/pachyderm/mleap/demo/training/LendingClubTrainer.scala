package ml.combust.pachyderm.mleap.demo.training
import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import com.databricks.spark.avro._
import ml.combust.bundle.BundleFile
import org.apache.spark.ml.bundle.SparkBundleContext
import org.apache.spark.ml.classification.{LogisticRegression, RandomForestClassifier}
import org.apache.spark.ml.{Pipeline, PipelineStage}
import org.apache.spark.ml.feature._
import ml.combust.mleap.spark.SparkSupport._

import resource._

/**
  * Created by hollinwilkins on 5/24/17.
  */
class LendingClubTrainer extends Trainer {
  override def sparkTrain(spark: SparkSession, config: Config): Unit = {
    val inputPath = config.getString("input")
    val outputPath = config.getString("output")

    assert(outputPath.endsWith(".zip"), "must output a zip MLeap bundle file, needs to end with .zip")
    assert(outputPath.startsWith("jar:file://"), "must output a zip MLeap bundle file, needs to begin with jar:file://")
    val (baseOutputPath, _) = outputPath.splitAt(outputPath.length - 4)
    val rfOutputPath = s"$baseOutputPath.rf.zip"
    val lrOutputPath = s"$baseOutputPath.lr.zip"

    val dataset = spark.sqlContext.read.avro(inputPath)

    dataset.createOrReplaceTempView("df")
    val datasetFnl = spark.sqlContext.sql(f"""
    select
        loan_amount,
        fico_score_group_fnl,
        case when dti >= 10.0
            then 10.0
            else dti
        end as dti,
        emp_length,
        case when state in ('CA', 'NY', 'MN', 'IL', 'FL', 'WA', 'MA', 'TX', 'GA', 'OH', 'NJ', 'VA', 'MI')
            then state
            else 'Other'
        end as state,
        loan_title,
        approved
    from df
    where loan_title in('Debt Consolidation', 'Other', 'Home/Home Improvement', 'Payoff Credit Card', 'Car Payment/Loan',
    'Business Loan', 'Health/Medical', 'Moving', 'Wedding/Engagement', 'Vacation', 'College', 'Renewable Energy', 'Payoff Bills',
    'Personal Loan', 'Motorcycle')
      """)

    val continuousFeatures = Array("loan_amount",
      "dti")

    val categoricalFeatures = Array("loan_title",
      "emp_length",
      "state",
      "fico_score_group_fnl")

    val allFeatures = continuousFeatures.union(categoricalFeatures)
    val allCols = allFeatures.union(Seq("approved")).map(datasetFnl.col)
    val nullFilter = allCols.map(_.isNotNull).reduce(_ && _)
    val datasetImputedFiltered = datasetFnl.select(allCols: _*).filter(nullFilter).persist()
    val Array(trainingDataset, validationDataset) = datasetImputedFiltered.randomSplit(Array(0.7, 0.3))
    val continuousFeatureAssembler = new VectorAssembler(uid = "continuous_feature_assembler").
      setInputCols(continuousFeatures).
      setOutputCol("unscaled_continuous_features")

    val continuousFeatureScaler = new StandardScaler(uid = "continuous_feature_scaler").
      setInputCol("unscaled_continuous_features").
      setOutputCol("scaled_continuous_features")

    val polyExpansionAssembler = new VectorAssembler(uid = "poly_expansion_feature_assembler").
      setInputCols(Array("loan_amount", "dti")).
      setOutputCol("poly_expansions_features")

    val continuousFeaturePolynomialExpansion = new PolynomialExpansion(uid = "polynomial_expansion_loan_amount").
      setInputCol("poly_expansions_features").
      setOutputCol("loan_amount_polynomial_expansion_features")

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

    val featureColsRf = categoricalFeatureIndexers.map(_.getOutputCol).union(Seq("scaled_continuous_features", "loan_amount_polynomial_expansion_features"))
    val featureColsLr = categoricalFeatureOneHotEncoders.map(_.getOutputCol).union(Seq("scaled_continuous_features"))

    // assemble all processes categorical and continuous features into a single feature vector
    val featureAssemblerLr = new VectorAssembler(uid = "feature_assembler_lr").
      setInputCols(featureColsLr).
      setOutputCol("features_lr")

    val featureAssemblerRf = new VectorAssembler(uid = "feature_assembler_rf").
      setInputCols(featureColsRf).
      setOutputCol("features_rf")

    val estimators: Array[PipelineStage] = Array(continuousFeatureAssembler, continuousFeatureScaler, polyExpansionAssembler, continuousFeaturePolynomialExpansion).
      union(categoricalFeatureIndexers).
      union(categoricalFeatureOneHotEncoders).
      union(Seq(featureAssemblerLr, featureAssemblerRf))

    val featurePipeline = new Pipeline(uid = "feature_pipeline").
      setStages(estimators)
    val sparkFeaturePipelineModel = featurePipeline.fit(datasetImputedFiltered)

    val randomForest = new RandomForestClassifier(uid = "random_forest_classifier").
      setFeaturesCol("features_rf").
      setLabelCol("approved").
      setPredictionCol("approved_prediction")

    val sparkPipelineEstimatorRf = new Pipeline().setStages(Array(sparkFeaturePipelineModel, randomForest))
    val sparkPipelineRf = sparkPipelineEstimatorRf.fit(datasetImputedFiltered)

    val logisticRegression = new LogisticRegression(uid = "logistic_regression").
      setFeaturesCol("features_lr").
      setLabelCol("approved").
      setPredictionCol("approved_prediction")

    val sparkPipelineEstimatorLr = new Pipeline().setStages(Array(sparkFeaturePipelineModel, logisticRegression))
    val sparkPipelineLr = sparkPipelineEstimatorLr.fit(datasetImputedFiltered)

    var sbc = SparkBundleContext().withDataset(sparkPipelineLr.transform(datasetImputedFiltered))
    (for(bf <- managed(BundleFile(lrOutputPath))) yield {
      sparkPipelineLr.writeBundle.save(bf)(sbc).get
    }).tried.get

    sbc = SparkBundleContext().withDataset(sparkPipelineRf.transform(datasetImputedFiltered))
    (for(bf <- managed(BundleFile(rfOutputPath))) yield {
      sparkPipelineRf.writeBundle.save(bf)(sbc).get
    }).tried.get
  }
}
