package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/admin/users}. */
public record CreateUserRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may only contain letters, digits, dot, underscore and hyphen")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email,

        @NotNull(message = "Role is required")
        Role role) {
}
