package net.villagerzock.corehandshake.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ServerTemplate(String name, String serverSoftware, String version) {
}
