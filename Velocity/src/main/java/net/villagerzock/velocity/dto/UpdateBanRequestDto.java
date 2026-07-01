package net.villagerzock.velocity.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateBanRequestDto(
        @Size(max = 300) String reason,
        Instant expiresAt
) {
}
