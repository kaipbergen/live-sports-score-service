package com.arshat.livescore.dto;

import com.arshat.livescore.domain.MatchStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMatchStatusRequest(
        @NotNull MatchStatus status
) {
}
