package com.stats.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActivityDataWithScore {

    ActivityData activityData;
    double score;
}
