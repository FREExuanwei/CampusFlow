package com.campusflow.modules.enrollment.dto;

import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import java.time.Instant;

public record MyEnrollment(long id, long activityId, Status status, Instant enrolledAt, Instant cancelledAt,
                           String activityTitle, Instant startsAt, Instant endsAt, ActivityStatus activityStatus) {
}
