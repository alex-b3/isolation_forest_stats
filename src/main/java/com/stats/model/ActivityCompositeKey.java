package com.stats.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ActivityCompositeKey implements Serializable {

    @Column(name = "Activity_ID")
    private int activityId;

    @Column(name = "Scenario")
    private String scenario;

    @Column(name = "Date")
    private LocalDate date;
}
