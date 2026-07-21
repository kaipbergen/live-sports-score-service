package com.arshat.livescore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * A single in-match event (goal, card, ...) that has been consumed from Kafka
 * and persisted. {@code externalId} carries the producer-generated UUID and is
 * unique, which makes event processing idempotent under Kafka's at-least-once
 * delivery.
 */
@Entity
@Table(name = "events", uniqueConstraints = @UniqueConstraint(columnNames = "external_id"))
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private UUID externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    private TeamSide team;

    private String player;

    private Integer minute;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Event() {
        // for JPA
    }

    public Event(UUID externalId, Match match, EventType type, TeamSide team, String player, Integer minute) {
        this.externalId = externalId;
        this.match = match;
        this.type = type;
        this.team = team;
        this.player = player;
        this.minute = minute;
    }

    public Long getId() {
        return id;
    }

    public UUID getExternalId() {
        return externalId;
    }

    public Match getMatch() {
        return match;
    }

    public EventType getType() {
        return type;
    }

    public TeamSide getTeam() {
        return team;
    }

    public String getPlayer() {
        return player;
    }

    public Integer getMinute() {
        return minute;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
