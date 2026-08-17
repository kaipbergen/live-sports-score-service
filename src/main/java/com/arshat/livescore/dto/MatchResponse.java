package com.arshat.livescore.dto;

import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;

import java.time.Instant;

public record MatchResponse(
        Long id,
        String homeTeam,
        String awayTeam,
        MatchStatus status,
        Instant startTime,
        Instant createdAt,
        Instant updatedAt
) {
    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                match.getStatus(),
                match.getStartTime(),
                match.getCreatedAt(),
                match.getUpdatedAt()
        );
    }
}
