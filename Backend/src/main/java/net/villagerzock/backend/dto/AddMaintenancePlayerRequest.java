package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AddMaintenancePlayerRequest(@NotBlank String player) {
}
