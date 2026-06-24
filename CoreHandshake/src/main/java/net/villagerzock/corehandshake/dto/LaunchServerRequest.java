package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotBlank;

public record LaunchServerRequest(@NotBlank String template, boolean singleton) {
}
