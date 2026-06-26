package net.villagerzock.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateNodeUserRequest(
        @NotBlank @Email String email,
        @Positive long roleId
) {
}
