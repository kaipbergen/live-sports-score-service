package com.arshat.livescore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchCreateMatchRequest(
        @NotEmpty @Size(max = 100) @Valid List<CreateMatchRequest> matches
) {
}
