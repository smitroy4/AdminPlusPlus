package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/task/{id}/status}. */
public record UpdateStatusRequest(@NotNull(message = "Status is required") TaskStatus status) {
}
