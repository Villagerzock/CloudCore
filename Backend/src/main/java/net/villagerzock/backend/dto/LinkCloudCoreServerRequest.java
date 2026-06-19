package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkCloudCoreServerRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "code must contain exactly six digits")
        String code
) {
}
