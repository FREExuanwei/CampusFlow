package com.campusflow.modules.activity.entity;

import com.campusflow.modules.activity.dto.ActivityForm;
import com.campusflow.modules.activity.enums.ActivityCategory;
import com.campusflow.modules.activity.enums.ActivityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 160)
    private String location;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "starts_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant startsAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "ends_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private ActivityCategory category = ActivityCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private ActivityStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private long createdBy;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "reviewed_at", columnDefinition = "datetime(6)")
    private Instant reviewedAt;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "published_at", columnDefinition = "datetime(6)")
    private Instant publishedAt;

    protected Activity() {}

    public Activity(ActivityForm form, long creator) {
        createdBy = creator;
        updateDraft(form);
    }

    public void updateDraft(ActivityForm form) {
        title = form.title().strip();
        description = form.description().strip();
        location = form.location().strip();
        startsAt = form.startsAt().truncatedTo(ChronoUnit.MICROS);
        endsAt = form.endsAt().truncatedTo(ChronoUnit.MICROS);
        capacity = form.capacity();
        if (form.category() != null) {
            category = form.category();
        }
        status = ActivityStatus.DRAFT;
        reviewedBy = null;
        reviewedAt = null;
        reviewReason = null;
    }

    public void submit() {
        status = ActivityStatus.SUBMITTED;
    }

    public void review(long actor, ActivityStatus outcome, String reason, Instant at) {
        status = outcome;
        reviewedBy = actor;
        reviewedAt = at;
        reviewReason = reason;
    }

    public void publish(Instant at) {
        status = ActivityStatus.PUBLISHED;
        publishedAt = at;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getCapacity() { return capacity; }
    public ActivityCategory getCategory() { return category; }
    public ActivityStatus getStatus() { return status; }
    public long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
    public Instant getPublishedAt() { return publishedAt; }
}
