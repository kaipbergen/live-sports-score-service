package com.arshat.livescore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "scores")
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    @Column(name = "home_goals", nullable = false)
    private int homeGoals = 0;

    @Column(name = "away_goals", nullable = false)
    private int awayGoals = 0;

    protected Score() {
        // for JPA
    }

    public Score(Match match) {
        this.match = match;
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public int getHomeGoals() {
        return homeGoals;
    }

    public int getAwayGoals() {
        return awayGoals;
    }

    public void addGoal(TeamSide side) {
        if (side == TeamSide.HOME) {
            homeGoals++;
        } else {
            awayGoals++;
        }
    }
}
