package com.arshat.livescore.dto;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.TeamSide;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload for the {@code match-events} topic. {@code eventId} is generated
 * by the producer and used by the consumer for idempotent processing.
 */
public record MatchEventMessage(
        UUID eventId,
        Long matchId,
        EventType type,
        TeamSide team,
        String player,
        Integer minute,
        Instant occurredAt
) {
}
