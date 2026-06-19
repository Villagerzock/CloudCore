package net.villagerzock.backend.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, String username) {
}
