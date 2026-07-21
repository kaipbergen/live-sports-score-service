package com.arshat.livescore.dto;

import com.arshat.livescore.domain.Event;
import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.TeamSide;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        EventType type,
        TeamSide team,
        String player,
        Integer minute,
        Instant createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getExternalId(),
                event.getType(),
                event.getTeam(),
                event.getPlayer(),
                event.getMinute(),
                event.getCreatedAt()
        );
    }
}
