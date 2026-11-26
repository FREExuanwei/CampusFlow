package com.campusflow.modules.enrollment.dto;

import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import java.time.Instant;

public record AdminEnrollment(long id, long userId, String username, Status status, Instant enrolledAt,
                              Instant cancelledAt) {
}
