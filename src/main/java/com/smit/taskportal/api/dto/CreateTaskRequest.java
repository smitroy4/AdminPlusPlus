package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/task}. */
public record CreateTaskRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 8000, message = "Description must not exceed 8000 characters")
        String description,

        @NotNull(message = "Priority is required")
        TaskPriority priority,

        Long assignedToId,

        Long clientId) {

    public CreateTaskRequest {
        if (description != null) {
            description = description.strip();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}
