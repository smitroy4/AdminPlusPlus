package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/task/{id}/escalate} — one entry of the escalation
 * conversation between a client and the managers/admins.
 */
public record EscalateRequest(

        @NotBlank(message = "Message body is required")
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String messageBody) {

    public EscalateRequest {
        if (messageBody != null) {
            messageBody = messageBody.strip();
        }
    }
}
