package com.campusflow.modules.activity.dto;

import com.campusflow.modules.activity.entity.Activity;
import com.campusflow.modules.activity.enums.ActivityCategory;
import com.campusflow.modules.activity.enums.ActivityPhase;
import java.time.Instant;

public record PublishedActivity(long id, String title, String description, String location,
                                Instant startsAt, Instant endsAt, int capacity, Instant publishedAt,
                                ActivityCategory category, ActivityPhase phase, int remainingSeats) {

    public static PublishedActivity from(Activity activity, long activeEnrollments, Instant now) {
        return new PublishedActivity(activity.getId(), activity.getTitle(), activity.getDescription(),
                activity.getLocation(), activity.getStartsAt(), activity.getEndsAt(),
                activity.getCapacity(), activity.getPublishedAt(), activity.getCategory(),
                ActivityPhase.at(activity.getStartsAt(), activity.getEndsAt(), now),
                (int) Math.max(0, activity.getCapacity() - activeEnrollments));
    }
}
