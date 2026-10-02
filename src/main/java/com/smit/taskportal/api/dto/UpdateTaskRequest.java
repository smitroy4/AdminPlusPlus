package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/task/{id}} — only these fields are mutable. */
public record UpdateTaskRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 8000, message = "Description must not exceed 8000 characters")
        String description,

        TaskPriority priority) {
}
