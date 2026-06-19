package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotBlank;

public record CommandRequest(@NotBlank String command) {
}
