package com.campusflow.modules.enrollment.dto;

import com.campusflow.modules.enrollment.entity.Enrollment;
import java.time.Instant;

public record EnrollmentView(long id, long activityId, Enrollment.Status status, Instant enrolledAt,
                             Instant cancelledAt) {
    public static EnrollmentView from(Enrollment enrollment) {
        return new EnrollmentView(enrollment.getId(), enrollment.getActivityId(), enrollment.getStatus(),
                enrollment.getEnrolledAt(), enrollment.getCancelledAt());
    }
}
