package net.villagerzock.velocity.dto;

import java.util.Map;
import java.util.UUID;

public record CallbackDto(UUID uuid, Map<String, Object> data) {
}
