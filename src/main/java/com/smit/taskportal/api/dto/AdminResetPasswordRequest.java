package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/admin/users/{id}/password}. */
public record AdminResetPasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password) {
}