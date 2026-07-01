package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateBannedPlayerRequest(
        @Size(max = 300) String reason,
        Instant expiresAt
) {
}
