package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LaunchServerRequest(@NotBlank String template, boolean singleton) {
}
