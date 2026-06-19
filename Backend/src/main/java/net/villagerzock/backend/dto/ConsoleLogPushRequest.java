package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConsoleLogPushRequest(
        @NotBlank String console,
        @NotEmpty List<@NotBlank String> lines
) {
}
