package com.arshat.livescore.dto;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.TeamSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotNull EventType type,
        TeamSide team,
        String player,
        @Min(0) @Max(130) Integer minute
) {
}
