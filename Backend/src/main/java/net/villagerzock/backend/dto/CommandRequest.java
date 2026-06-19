package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CommandRequest(@NotBlank String command) {
}
