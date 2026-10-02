package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.TaskMessage;

import java.time.Instant;

/** Read model for one entry of a task's message thread. */
public record TaskMessageDto(Long id,
                             Long taskId,
                             UserSummaryDto fromUser,
                             String messageBody,
                             boolean internal,
                             Instant createdAt) {

    public static TaskMessageDto from(TaskMessage message) {
        return new TaskMessageDto(
                message.getId(),
                message.getTask() == null ? null : message.getTask().getId(),
                UserSummaryDto.from(message.getFromUser()),
                message.getMessageBody(),
                message.isInternal(),
                message.getCreatedAt());
    }
}
