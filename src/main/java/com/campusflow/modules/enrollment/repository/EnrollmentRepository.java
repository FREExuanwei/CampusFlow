package com.campusflow.modules.enrollment.repository;

import com.campusflow.modules.enrollment.dto.AdminEnrollment;
import com.campusflow.modules.enrollment.dto.MyEnrollment;
import com.campusflow.modules.enrollment.entity.Enrollment;
import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByActivityIdAndUserId(long activityId, long userId);

    long countByActivityIdAndStatus(long activityId, Enrollment.Status status);

    String MY_ENROLLMENT_SCOPE = """
            from Enrollment enrollment join Activity activity on activity.id = enrollment.activityId
            where enrollment.userId = :userId
            """;

    String MY_ENROLLMENT = """
            select new com.campusflow.modules.enrollment.dto.MyEnrollment(
                enrollment.id, enrollment.activityId, enrollment.status, enrollment.enrolledAt, enrollment.cancelledAt,
                activity.title, activity.startsAt, activity.endsAt, activity.status)
            """ + MY_ENROLLMENT_SCOPE;

    String MY_ENROLLMENT_FILTER = """
            and (:status is null or enrollment.status = :status)
            and (:phase is null
                or (:phase = 'UPCOMING' and activity.startsAt > :now)
                or (:phase = 'ONGOING' and activity.startsAt <= :now and activity.endsAt > :now)
                or (:phase = 'ENDED' and activity.endsAt <= :now))
            """;

    @Query("""
            select enrollment.activityId as activityId, count(enrollment) as total
            from Enrollment enrollment where enrollment.activityId in :ids and enrollment.status = :status
            group by enrollment.activityId
            """)
    List<ActivityCount> countByActivityIdsAndStatus(List<Long> ids, Status status);

    @Query(value = MY_ENROLLMENT + MY_ENROLLMENT_FILTER,
            countQuery = "select count(enrollment) " + MY_ENROLLMENT_SCOPE + MY_ENROLLMENT_FILTER)
    Page<MyEnrollment> findMine(long userId, Status status, String phase, Instant now, Pageable pageable);

    @Query(MY_ENROLLMENT + " and enrollment.id = :id")
    Optional<MyEnrollment> findMineById(long userId, long id);

    @Query(value = """
            select new com.campusflow.modules.enrollment.dto.AdminEnrollment(
                enrollment.id, enrollment.userId, account.username, enrollment.status,
                enrollment.enrolledAt, enrollment.cancelledAt)
            from Enrollment enrollment join UserAccount account on account.id = enrollment.userId
            where enrollment.activityId = :activityId and (:status is null or enrollment.status = :status)
            """, countQuery = """
            select count(enrollment) from Enrollment enrollment
            where enrollment.activityId = :activityId and (:status is null or enrollment.status = :status)
            """)
    Page<AdminEnrollment> findRoster(long activityId, Status status, Pageable pageable);

    @Query("""
            select enrollment.status as status, count(enrollment) as total
            from Enrollment enrollment where enrollment.activityId = :activityId group by enrollment.status
            """)
    List<StatusCount> countByStatus(long activityId);

    interface ActivityCount {
        long getActivityId();
        long getTotal();
    }

    interface StatusCount {
        Status getStatus();
        long getTotal();
    }
}
