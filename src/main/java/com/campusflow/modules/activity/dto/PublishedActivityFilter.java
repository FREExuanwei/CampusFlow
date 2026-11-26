package com.campusflow.modules.activity.dto;

import com.campusflow.modules.activity.enums.ActivityCategory;
import com.campusflow.modules.activity.enums.ActivityPhase;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public record PublishedActivityFilter(
        @Size(max = 120) String title,
        @Size(max = 120) String keyword,
        ActivityCategory category,
        ActivityPhase phase,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startsFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startsBefore) {
}
