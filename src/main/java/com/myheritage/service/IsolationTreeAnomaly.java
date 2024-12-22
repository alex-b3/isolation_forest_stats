package com.myheritage.service;

import com.myheritage.model.ActivityCompositeKey;
import com.myheritage.model.ActivityData;
import com.myheritage.model.ActivityDataWithScore;
import com.myheritage.repository.ActivityDataRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;
import smile.math.MathEx;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IsolationTreeAnomaly {

    private final ActivityDataRepository repository;

    public IsolationTreeAnomaly(ActivityDataRepository repository) {
        this.repository = repository;
    }

    public List<ActivityDataWithScore> calculateAnomalyIsolationForest(int activityId, int windowDays) {

        List<ActivityData> activityDataList;
        List<ActivityDataWithScore> allAnomalies = new ArrayList<>();
        activityDataList = (windowDays == 0) ? findByActivityId(activityId) : findByActivityIdAndWindowDays(activityId, windowDays);


        // Group by scenario
        Map<String, List<ActivityData>> groupedByScenario = activityDataList.stream()
                .collect(Collectors.groupingBy(data -> data.getId().getScenario()));

        groupedByScenario.forEach((scenario, dataList) -> {

            List<LocalDate> allDates = getAllDatesForActivityList(dataList);

            Set<LocalDate> existingDates = dataList.stream()
                    .map(activityData -> activityData.getId().getDate())
                    .collect(Collectors.toSet());

            List<LocalDate> missingDates = allDates.stream()
                    .filter(date -> !existingDates.contains(date))
                    .collect(Collectors.toList());

            missingDates.forEach(date -> {
                dataList.add(new ActivityData(new ActivityCompositeKey(activityId, scenario, date), 0));
            });

            dataList.sort(Comparator.comparing(activityData -> activityData.getId().getDate()));

            Map<String, Double>  minMax = findGlobalCounterMinMax(dataList);

            double[][] data = getFilledData(dataList, minMax);

            IsolationForest iforest = IsolationForest.fit(data, 100, (int) MathEx.log2(data.length), 0.9, 0);
            double[] scores = iforest.score(data);

            List<ActivityDataWithScore> dataListWithScores = new ArrayList<>();
            for (int i = 0; i < dataList.size(); i++) {
                dataListWithScores.add(new ActivityDataWithScore(dataList.get(i), scores[i]));
            }

            // Dynamic threshold calculation, e.g., using the 95th percentile of scores
            Arrays.sort(scores);
            double meanScore = Arrays.stream(scores).average().orElse(Double.NaN);
            double stdDev = Math.sqrt(Arrays.stream(scores).map(score -> Math.pow(score - meanScore, 2)).average().orElse(Double.NaN));
//            double threshold = scores[(int) Math.ceil(scores.length * 0.95) - 1];
            double threshold = meanScore + 2 * stdDev;
            System.out.println("Scenario is: " + scenario + " Threshold is: " + threshold);

            dataListWithScores.stream()
                    .filter(dataListWithScore -> dataListWithScore.getScore() > threshold)
                    .forEach(dataListWithScore -> {
                        System.out.println(dataListWithScore.getActivityData() + " " + dataListWithScore.getScore());
                        allAnomalies.add(dataListWithScore);
                    });

        });

        double stdDevAnomaly = calculateStandardDeviation(activityDataList);
        double meaningfulChangeThreshold = 2 * stdDevAnomaly;

        return allAnomalies.stream()
                .filter(ads -> {
                    // Calculate the change magnitude for the current activity data compared to the mean of dataset
                    double meanCounter = activityDataList.stream().mapToDouble(ActivityData::getCounter).average().orElse(0.0);
                    double changeMagnitude = Math.abs(ads.getActivityData().getCounter() - meanCounter);

                    // Only include anomalies where the change magnitude exceeds the meaningful change threshold
                    return changeMagnitude > meaningfulChangeThreshold;
                })
                .collect(Collectors.toList());
    }

    private double[][] getFilledData(List<ActivityData> dataList, Map<String, Double>  minMax) {
        double[][] data = new double[dataList.size()][2]; // Preparing Data for 2 features
        for (int i = 0; i < dataList.size(); i++) {
            ActivityData currentActivity = dataList.get(i);

            // Normalize features
            data[i][0] = getNormalizedCounter(currentActivity, minMax.get("counterMin"), minMax.get("counterMax")); // Normalized counter
            data[i][1] = getNormalizedChangeRatio( i, dataList, currentActivity); // Normalized change ratio
        }
        return data;
    }

    @NotNull
    private static List<LocalDate> getAllDatesForActivityList(List<ActivityData> activityDataList) {
        // Find the date range within the retrieved data
        LocalDate startDate = activityDataList.stream()
                .map(activityData -> activityData.getId().getDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now()); // Fallback to current date if no data found

        LocalDate endDate = activityDataList.stream()
                .map(activityData -> activityData.getId().getDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now()); // Fallback to current date if no data found

        return startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
    }

    public List<ActivityData> findByActivityId(int activityId) {
        return repository.findByIdActivityIdNative(activityId);
    }

    public List<ActivityData> findByActivityIdAndWindowDays(int activityId, int windowDays) {
        return repository.findByActivityIdAndWindowDays(activityId, windowDays);
    }

    private double calculateAveragePastNdays(List<ActivityData> dataList, int currentIndex, int lookBackDays) {
        int start = Math.max(0, currentIndex - lookBackDays);
        int count = 0;
        double sum = 0;
        for (int i = start; i < currentIndex; i++) {
            sum += dataList.get(i).getCounter();
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private double getNormalizedChangeRatio(int index, List<ActivityData> activityDataListForAllDates, ActivityData currentActivity){

        double ratioMax = Double.MAX_VALUE;
        double ratioMin = Double.MIN_VALUE;
        double averagePast = calculateAveragePastNdays(activityDataListForAllDates, index, 7);
        double changeRatio = averagePast != 0 ? currentActivity.getCounter() / averagePast : 0;

        ratioMin = Math.min(ratioMin, changeRatio);
        ratioMax = Math.max(ratioMax, changeRatio);
        return  (changeRatio - ratioMin) / (ratioMax - ratioMin);
    }

    private double getNormalizedCounter(ActivityData currentActivity, double counterMin, double counterMax){
        // Ensure there's no division by zero
        if (counterMax == counterMin) return 0; // Or handle this case as you see fit
        return (currentActivity.getCounter() - counterMin) / (counterMax - counterMin);
    }

    public Map<String, Double> findGlobalCounterMinMax(List<ActivityData> dataList) {
        double counterMin = Double.MAX_VALUE;
        double counterMax = Double.MIN_VALUE;

        for (ActivityData activity : dataList) {
            counterMin = Math.min(counterMin, activity.getCounter());
            counterMax = Math.max(counterMax, activity.getCounter());
        }

        Map<String, Double> minMaxMap = new HashMap<>();
        minMaxMap.put("counterMin", counterMin);
        minMaxMap.put("counterMax", counterMax);

        return minMaxMap;
    }

    private double calculateStandardDeviation(List<ActivityData> dataList) {
        double mean = dataList.stream()
                .mapToDouble(ActivityData::getCounter)
                .average().orElse(0.0);
        double variance = dataList.stream()
                .mapToDouble(a -> Math.pow(a.getCounter() - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }
}
