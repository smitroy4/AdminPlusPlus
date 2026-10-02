package com.smit.taskportal.api.dto;

import java.util.List;

/**
 * Payload of {@code GET /api/task/{id}} — the task plus its visible thread.
 * {@code clientDetails} carries the client contact block and is non-null only
 * for MANAGER / ADMIN callers.
 */
public record TaskDetailDto(TaskDto task,
                            List<TaskMessageDto> messages,
                            long messageCount,
                            boolean internalVisible,
                            ClientDto clientDetails) {
}
