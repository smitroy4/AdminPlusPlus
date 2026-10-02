package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Role;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/users/{id}/role}. */
public record UpdateRoleRequest(@NotNull(message = "Role is required") Role role) {
}
