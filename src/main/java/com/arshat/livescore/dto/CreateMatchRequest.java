package com.arshat.livescore.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateMatchRequest(
        @NotBlank String homeTeam,
        @NotBlank String awayTeam,
        Instant startTime
) {
}
