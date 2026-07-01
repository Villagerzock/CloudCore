package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBannedPlayerRequest(
        @NotBlank @Size(max = 64) String player,
        @NotBlank @Size(max = 300) String reason,
        Instant expiresAt
) {
}
