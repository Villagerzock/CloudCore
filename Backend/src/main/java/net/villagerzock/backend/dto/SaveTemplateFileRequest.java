package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotNull;

public record SaveTemplateFileRequest(
        boolean binary,
        @NotNull String content
) {
}
