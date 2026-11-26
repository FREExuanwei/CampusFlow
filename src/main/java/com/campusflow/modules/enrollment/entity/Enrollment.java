package com.campusflow.modules.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    public enum Status { ENROLLED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private long activityId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Status status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "enrolled_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enrolledAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cancelled_at", columnDefinition = "datetime(6)")
    private Instant cancelledAt;

    protected Enrollment() {}

    public Enrollment(long activityId, long userId, Instant enrolledAt) {
        this.activityId = activityId;
        this.userId = userId;
        enroll(enrolledAt);
    }

    public void enroll(Instant at) {
        status = Status.ENROLLED;
        enrolledAt = at;
        cancelledAt = null;
    }

    public void cancel(Instant at) {
        status = Status.CANCELLED;
        cancelledAt = at;
    }

    public Long getId() { return id; }
    public long getActivityId() { return activityId; }
    public Status getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
