package com.myheritage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityDataExtended implements Serializable {

    private int counter;
    private int activityId;
    private String scenario;
    private long date;
    @IncludeInVectorAssembler private int dayOfWeek;
    @IncludeInVectorAssembler private int dayOfMonth;
    @IncludeInVectorAssembler private int monthOfYear;
    @IncludeInVectorAssembler private int quarterOfYear;
    @IncludeInVectorAssembler private int year;
}
