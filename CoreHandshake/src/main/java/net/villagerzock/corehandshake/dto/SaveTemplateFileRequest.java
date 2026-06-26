package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotNull;

public record SaveTemplateFileRequest(
        boolean binary,
        @NotNull String content
) {
}
