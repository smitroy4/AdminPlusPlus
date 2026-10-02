package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/task/{id}/message}.
 *
 * <p>{@code internal} is boxed so that omitting it from the JSON payload is not
 * mistaken for an explicit {@code null} primitive.
 */
public record CreateMessageRequest(

        @NotBlank(message = "Message body is required")
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String messageBody,

        Boolean internal) {

    public CreateMessageRequest {
        if (messageBody != null) {
            messageBody = messageBody.strip();
        }
    }

    public boolean internalNote() {
        return Boolean.TRUE.equals(internal);
    }
}
