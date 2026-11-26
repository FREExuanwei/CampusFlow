package com.campusflow.modules.activity.dto;

import com.campusflow.modules.activity.entity.Activity;
import com.campusflow.modules.activity.enums.ActivityCategory;
import com.campusflow.modules.activity.enums.ActivityStatus;
import java.time.Instant;

public record ActivityView(long id, String title, String description, String location,
                           Instant startsAt, Instant endsAt, int capacity, ActivityStatus status,
                           long createdBy, Instant createdAt, Instant updatedAt, long version,
                           Long reviewedBy, Instant reviewedAt, String reviewReason, Instant publishedAt,
                           ActivityCategory category) {

    public static ActivityView from(Activity activity) {
        return new ActivityView(activity.getId(), activity.getTitle(), activity.getDescription(),
                activity.getLocation(), activity.getStartsAt(), activity.getEndsAt(), activity.getCapacity(),
                activity.getStatus(), activity.getCreatedBy(), activity.getCreatedAt(),
                activity.getUpdatedAt(), activity.getVersion(), activity.getReviewedBy(),
                activity.getReviewedAt(), activity.getReviewReason(), activity.getPublishedAt(), activity.getCategory());
    }
}
