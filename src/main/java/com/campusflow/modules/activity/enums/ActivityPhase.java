package com.campusflow.modules.activity.enums;

import java.time.Instant;

public enum ActivityPhase {
    UPCOMING, ONGOING, ENDED;

    public static ActivityPhase at(Instant startsAt, Instant endsAt, Instant now) {
        if (startsAt.isAfter(now)) {
            return UPCOMING;
        }
        return endsAt.isAfter(now) ? ONGOING : ENDED;
    }
}
