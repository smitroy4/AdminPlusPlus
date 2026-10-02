package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;

import java.time.Instant;

/** Read model for a task. Relations are already initialised, no lazy proxies. */
public record TaskDto(Long id,
                      String taskNo,
                      String title,
                      String description,
                      TaskStatus status,
                      TaskPriority priority,
                      UserSummaryDto assignedTo,
                      UserSummaryDto createdBy,
                      ClientSummaryDto client,
                      Instant createdAt,
                      Instant updatedAt) {

    public static TaskDto from(Task task) {
        return new TaskDto(
                task.getId(),
                task.getTaskNo(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                UserSummaryDto.from(task.getAssignedTo()),
                UserSummaryDto.from(task.getCreatedBy()),
                ClientSummaryDto.from(task.getClient()),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
