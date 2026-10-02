package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/task/{id}/assign}. A {@code null} userId releases the
 * task back to the unassigned pool (manager only).
 */
public record AssignTaskRequest(@NotNull(message = "userId is required") Long userId) {
}
