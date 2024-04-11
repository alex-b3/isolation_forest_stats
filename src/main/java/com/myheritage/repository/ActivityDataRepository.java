package com.myheritage.repository;

import com.myheritage.model.ActivityCompositeKey;
import com.myheritage.model.ActivityData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ActivityDataRepository extends JpaRepository<ActivityData, ActivityCompositeKey> {

    @Query(value = "SELECT * FROM daily_activities WHERE Activity_ID = ?1", nativeQuery = true)
    List<ActivityData> findByIdActivityIdNative(int activityId);

    @Query(value = "SELECT * FROM daily_activities WHERE Activity_ID = ?1 AND Date >= CURRENT_DATE - INTERVAL ?2 DAY", nativeQuery = true)
    List<ActivityData> findByActivityIdAndWindowDays(int activityId, int windowDays);

}
