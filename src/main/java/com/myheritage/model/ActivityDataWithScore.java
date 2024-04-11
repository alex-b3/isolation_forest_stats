package com.myheritage.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActivityDataWithScore {

    ActivityData activityData;
    double score;
}
