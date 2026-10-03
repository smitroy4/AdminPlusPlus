package com.smit.taskportal.api.dto;

import java.util.List;

/**
 * Payload of {@code GET /api/task/{id}} — the task plus its visible thread.
 * {@code clientDetails} carries the client contact block and is non-null only
 * for MANAGER / ADMIN callers.
 *
 * <p>{@code escalations} is the private "escalate to manager" conversation and
 * is only ever populated for its participants (managers/admins and the client
 * who escalated); {@code escalationParticipant} tells the UI whether the caller
 * may read and reply to it.
 */
public record TaskDetailDto(TaskDto task,
                            List<TaskMessageDto> messages,
                            long messageCount,
                            boolean internalVisible,
                            ClientDto clientDetails,
                            List<TaskMessageDto> escalations,
                            boolean escalated,
                            boolean escalationParticipant) {
}
