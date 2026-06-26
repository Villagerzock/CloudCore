package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotBlank;

public record AddMaintenancePlayerRequest(@NotBlank String player) {
}
