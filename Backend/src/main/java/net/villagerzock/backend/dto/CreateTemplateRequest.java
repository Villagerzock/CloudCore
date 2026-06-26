package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String serverSoftware,
        @NotBlank String version,
        @NotBlank String memory,
        @NotBlank String worldType,
        String superflatType,
        String seed
) {
}
