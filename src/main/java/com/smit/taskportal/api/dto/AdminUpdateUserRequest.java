package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/admin/users/{id}} — every field optional. */
public record AdminUpdateUserRequest(
        @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may only contain letters, digits, dot, underscore and hyphen")
        String username,

        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email) {

    public boolean hasChanges() {
        return username != null || email != null;
    }
}