package com.arshat.livescore.dto;

import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.Score;

public record ScoreResponse(
        Long matchId,
        String homeTeam,
        String awayTeam,
        int homeGoals,
        int awayGoals,
        MatchStatus status
) {
    public static ScoreResponse from(Match match, Score score) {
        return new ScoreResponse(
                match.getId(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                score.getHomeGoals(),
                score.getAwayGoals(),
                match.getStatus()
        );
    }
}
