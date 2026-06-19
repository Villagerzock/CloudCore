package net.villagerzock.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record LinkCodeResponse(UUID serverId, String code, Instant expiresAt) {
}
