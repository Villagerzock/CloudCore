package net.villagerzock.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record LinkedInstanceResponse(UUID instanceId, String name, Instant linkedAt) {
}
