package com.myheritage.controllers;

import com.myheritage.model.ActivityDataExtended;
import com.myheritage.model.ActivityDataWithScore;
import com.myheritage.service.IsolationTreeAnomaly;
import com.myheritage.service.KMeansAnomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/anomalies")
public class AnomaliesController {

    private final IsolationTreeAnomaly isolationTreeAnomaly;

    private final KMeansAnomaly kMeansAnomaly;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public AnomaliesController(IsolationTreeAnomaly isolationTreeAnomaly, KMeansAnomaly kMeansAnomaly) {
        this.isolationTreeAnomaly = isolationTreeAnomaly;
        this.kMeansAnomaly = kMeansAnomaly;
    }


    @GetMapping
    @ResponseBody
    public ResponseEntity calculateAnomaly(String modelType, int activityId, @RequestParam(required = false) Optional<String> windowDays){

        ResponseEntity response;

        try {
            String result;
            switch (modelType){
                case "isolation_tree":
                    result = isolationTreeAnomaly.calculateAnomalyIsolationForest(activityId,
                                    Integer.parseInt(windowDays.orElse("0")))
                            .stream()
                            .map(ActivityDataWithScore::toString)
                            .collect(Collectors.joining("<br>"));
                    break;
                case "k_means":
                    result = kMeansAnomaly.calculateAnomalyKMeans(activityId, Integer.parseInt(windowDays.orElse("0"))).stream()
                    .map(ActivityDataExtended::toString)
                    .collect(Collectors.joining("<br>"));
                    break;
                default:
                    result = "No such type exists in the code";
            }

            response = ResponseEntity.status(HttpStatus.OK)
                    .body(result);
            logger.debug("");
        } catch(Exception exception){
            response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("");
        }
        return response;
    }
}
