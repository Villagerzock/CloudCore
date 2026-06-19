package net.villagerzock.backend.dto;

import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ServerTemplateDto(
        String name,
        String serverSoftware,
        String version
) {
}
