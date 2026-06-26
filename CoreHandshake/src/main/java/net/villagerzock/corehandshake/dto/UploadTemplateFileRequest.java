package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadTemplateFileRequest(
        @NotBlank String relativePath,
        @NotNull String content
) {
}
