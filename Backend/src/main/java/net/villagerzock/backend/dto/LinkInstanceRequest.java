package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LinkInstanceRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "code must contain exactly six digits")
        String code,

        @NotBlank
        @Size(max = 100)
        String name
) {
}
