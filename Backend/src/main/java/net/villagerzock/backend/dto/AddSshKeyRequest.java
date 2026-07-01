package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddSshKeyRequest(
        @NotBlank @Size(max = 4096) String key
) {
}
