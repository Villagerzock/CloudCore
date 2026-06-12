package net.villagerzock.cloudcore.core.server.dto;

import java.util.Map;
import java.util.UUID;

public record CallbackDto(UUID uuid, Map<String, Object> data) {
}
