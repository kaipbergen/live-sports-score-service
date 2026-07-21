package com.arshat.livescore.dto;

import java.util.UUID;

public record EventAcceptedResponse(UUID eventId, String status) {
}
