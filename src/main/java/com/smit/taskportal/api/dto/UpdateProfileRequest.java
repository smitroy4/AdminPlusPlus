package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/me/profile} — the caller's own contact details. */
public record UpdateProfileRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email) {
}