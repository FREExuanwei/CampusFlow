package com.campusflow.modules.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ActivityTransitions {

    public enum Decision { APPROVE, REJECT }

    public record Version(@NotNull @PositiveOrZero Long version) {}

    public record Review(@NotNull @PositiveOrZero Long version, @NotNull Decision decision,
                         @Size(max = 500) String reason) {}

    private ActivityTransitions() {}
}
