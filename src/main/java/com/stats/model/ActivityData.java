package com.stats.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.io.Serializable;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityData implements Serializable {

    @EmbeddedId
    private ActivityCompositeKey id;

    @Column(name = "Counter")
    private int counter;

}
