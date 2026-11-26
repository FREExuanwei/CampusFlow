package com.campusflow.modules.activity.dto;

import com.campusflow.modules.activity.enums.ActivityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ActivityForm(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 160) String location,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Positive Integer capacity,
        @PositiveOrZero Long version,
        ActivityCategory category) {
}
