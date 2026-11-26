package com.campusflow.modules.activity.repository;

import com.campusflow.modules.activity.entity.Activity;
import com.campusflow.modules.activity.enums.ActivityStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    Optional<Activity> findByIdAndStatus(long id, ActivityStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select activity from Activity activity where activity.id = :id")
    Optional<Activity> findLockedById(@Param("id") long id);

    @Query("""
            select activity from Activity activity
            where (:status is null or activity.status = :status)
              and (:title is null or locate(lower(:title), lower(activity.title)) > 0)
            """)
    Page<Activity> search(@Param("status") ActivityStatus status, @Param("title") String title, Pageable pageable);
}
