package com.stats.service;

import com.stats.model.ActivityData;
import com.stats.model.ActivityDataExtended;
import com.stats.model.IncludeInVectorAssembler;
import com.stats.repository.ActivityDataRepository;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.evaluation.ClusteringEvaluator;
import org.apache.spark.ml.feature.OneHotEncoder;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;

@Service
public class KMeansAnomaly {

    private final ActivityDataRepository repository;

    private final SparkSession spark;

    public KMeansAnomaly(ActivityDataRepository repository, SparkSession spark) {
        this.repository = repository;
        this.spark = spark;
    }

    public List<ActivityDataExtended> calculateAnomalyKMeans(int activityId, int windowDays) {

        List<ActivityData> activityData = (windowDays == 0) ? repository.findByIdActivityIdNative(activityId) : repository.findByActivityIdAndWindowDays(activityId, windowDays);


        List<ActivityDataExtended> activityDataExtendedList = activityData.stream()
                .map(activity ->
                        new ActivityDataExtended(
                                activity.getCounter(),
                                activity.getId().getActivityId(),
                                activity.getId().getScenario(),
                                activity.getId().getDate()
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .toEpochSecond(),
                                activity.getId().getDate().getDayOfWeek().getValue(),
                                activity.getId().getDate().getDayOfMonth(),
                                activity.getId().getDate().getMonthValue(),
                                ((activity.getId().getDate().getMonthValue() - 1) / 3 + 1),
                                activity.getId().getDate().getYear())).collect(Collectors.toList());


        Map<String, List<ActivityDataExtended>> groupedByScenario = activityDataExtendedList.stream()
                .collect(Collectors.groupingBy(ActivityDataExtended::getScenario));

        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());

        List<ActivityDataExtended> anomaliesWithScenarios = new ArrayList<>();
        groupedByScenario.forEach((scenario, data) -> {
            JavaRDD<ActivityDataExtended> myObjectRDD = jsc.parallelize(data);
            Dataset<Row> activityDS = spark.createDataset(myObjectRDD.rdd(), Encoders.bean(ActivityDataExtended.class)).toDF();

            activityDS.show();

            String[] selectedFieldNames = getFieldNamesForArray(ActivityDataExtended.class);

            Dataset<Row> transformedDS = transformFeatures(activityDS, selectedFieldNames).select("date", "scenario", "features");

            transformedDS.printSchema();
            transformedDS.show();
            Dataset<Row> predictions;

            int bestNumClusters = 2;
            int bestK = 0;
            double bestSilhouette = Double.NEGATIVE_INFINITY;
            int maxK = Math.min(data.size(), 20);
            for (int k = 2; k <= maxK; k++) {
                KMeans kMeans = new KMeans().setK(k).setFeaturesCol("features").setPredictionCol("prediction");
                KMeansModel model = kMeans.fit(transformedDS);
                predictions = model.transform(transformedDS);
                ClusteringEvaluator evaluator = new ClusteringEvaluator();
                double silhouette = evaluator.evaluate(predictions);
                System.out.println("For k = " + k + " silhouette is : " + silhouette);
                if (silhouette > bestSilhouette) {
                    bestK = k;
                    bestSilhouette = silhouette;
                    bestNumClusters = k;
                    System.out.println("The best silhouette was changed to " + bestSilhouette + " with k " + bestNumClusters);
                }
            }

            System.out.println("The best silhouette was " + bestSilhouette + "with best K - " + bestK);

            KMeans kMeans = new KMeans().setK(maxK).setFeaturesCol("features").setPredictionCol("prediction");
            KMeansModel model = kMeans.fit(transformedDS);
            predictions = model.transform(transformedDS);

//            KMeans kMeans = new KMeans().setK(200).setFeaturesCol("features").setPredictionCol("prediction");
//            KMeansModel model = kMeans.fit(transformedDS);
//
//            Dataset<Row> predictions = model.transform(transformedDS);
//
//            ClusteringEvaluator evaluator = new ClusteringEvaluator();
//            double silhouette = evaluator.evaluate(predictions);
//            System.out.println("Silhouette with squared euclidean distance = " + silhouette);

            predictions.show();

            Vector[] clusterCenters = model.clusterCenters();
            for (Vector center : clusterCenters) {
                System.out.println(center);
            }

            Dataset<Row> clusterSizes = predictions.groupBy("prediction").count();
            clusterSizes.show();

// Calculate the threshold for anomalies. Here, we're using the 25th percentile as an arbitrary cutoff.
// This value should be chosen based on your specific use case and understanding of the data.
            double threshold = clusterSizes.stat().approxQuantile("count", new double[]{0.25}, 0.05)[0];
            System.out.println("Threshold for anomaly detection: " + threshold);

// Filter out clusters that are considered anomalies based on our threshold.
            Dataset<Row> anomalies = clusterSizes.filter("count < " + threshold);
            anomalies.show();

// Optionally, collect the IDs of the anomalous clusters for further analysis or action
            List<Row> anomalousClusters = anomalies.select("prediction").collectAsList();
            for (Row r : anomalousClusters) {
                System.out.println("Anomalous Cluster ID: " + r.get(0));
                // Further analysis or action here
            }

            List<Row> anomalousClusterIds = anomalies.select("prediction").collectAsList();

            // Convert the list of anomalous cluster IDs to a format that can be used in a SQL IN clause
            String anomalousClusterIdsInClause = anomalousClusterIds.stream()
                    .map(row -> String.valueOf(row.getInt(0)))
                    .collect(Collectors.joining(",", "(", ")"));

            // Assuming anomalousClusterIds is a List<Row> of anomalous cluster IDs
            List<Integer> anomalousClusterIdsList = anomalousClusterIds.stream()
                    .map(row -> row.getInt(0))
                    .collect(Collectors.toList());

// Filter predictions DataFrame to include only the anomalies
            Dataset<Row> anomalousPredictions = predictions.filter(col("prediction").isin(anomalousClusterIdsList.toArray()));

            Dataset<Row> activityDSAlias = activityDS.alias("activity");
            Dataset<Row> predictionsAlias = predictions.alias("predictions");

// Perform the join using the aliased DataFrames and qualified column names
            Dataset<Row> activityDSAnomalies = activityDSAlias
                    .join(anomalousPredictions,
                            activityDSAlias.col("activity.date").equalTo(anomalousPredictions.col("date"))
                                    .and(activityDSAlias.col("activity.scenario").equalTo(anomalousPredictions.col("scenario"))),
                            "inner")
                    .select(
                            activityDSAlias.col("activity.activityId"),
                            activityDSAlias.col("activity.counter"),
                            activityDSAlias.col("activity.date"),
                            activityDSAlias.col("activity.dayOfMonth"),
                            activityDSAlias.col("activity.dayOfWeek"),
                            activityDSAlias.col("activity.monthOfYear"),
                            activityDSAlias.col("activity.quarterOfYear"),
                            activityDSAlias.col("activity.scenario"), // No ambiguity now
                            activityDSAlias.col("activity.year"),
                            predictionsAlias.col("predictions.prediction"),
                            predictionsAlias.col("predictions.features") // No ambiguity now
                    );

// Show the filtered anomalous records
            activityDSAnomalies.show();
            activityDSAnomalies.printSchema();

            // Example: Converting back to ActivityDataExtended instances
            List<ActivityDataExtended> anomaliesList = activityDSAnomalies.as(Encoders.bean(ActivityDataExtended.class)).collectAsList();
            anomaliesWithScenarios.addAll(anomaliesList);
            String anomalyScenario = anomaliesWithScenarios.size() == 0 ? " no Scenario " : anomaliesWithScenarios.get(0).getScenario();
            System.out.println("Anomalies count finished for Scenario: " + anomalyScenario);

        });


        return anomaliesWithScenarios;
    }

    public static String[] getFieldNamesForArray(Class<?> clazz) {
        List<String> fieldNames = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(IncludeInVectorAssembler.class)) {
                fieldNames.add(field.getName());
            }
        }

        return fieldNames.toArray(new String[0]);
    }

    public Dataset<Row> transformFeatures(Dataset<Row> activityDS, String[] featuresArr){

        List<String> inputColsList = new ArrayList<>();
        inputColsList.add("counter");

        for(String feature: featuresArr) {
            long distinctCount = activityDS.select(feature).distinct().count();

            if (distinctCount > 1) {
                String indexerOutputCol = feature + "Index";
                String encoderOutputCol = feature + "Vector";

                StringIndexer indexer = new StringIndexer()
                        .setInputCol(feature)
                        .setOutputCol(indexerOutputCol);
                activityDS = indexer.fit(activityDS).transform(activityDS);

                OneHotEncoder encoder = new OneHotEncoder()
                        .setInputCols(new String[]{indexerOutputCol})
                        .setOutputCols(new String[]{encoderOutputCol});
                activityDS = encoder.fit(activityDS).transform(activityDS);

                inputColsList.add(encoderOutputCol);
            }
            else {
                System.out.println("Skipping feature " + feature + " due to insufficient distinct values.");
            }
        }

        String[] inputColsArray = inputColsList.toArray(new String[0]);

        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(inputColsArray)
                .setOutputCol("features");

        return assembler.transform(activityDS);
    }
}
