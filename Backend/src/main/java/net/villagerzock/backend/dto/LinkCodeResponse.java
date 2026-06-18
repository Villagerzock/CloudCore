package net.villagerzock.backend.dto;

import java.time.Instant;

public record LinkCodeResponse(String code, Instant expiresAt) {
}
