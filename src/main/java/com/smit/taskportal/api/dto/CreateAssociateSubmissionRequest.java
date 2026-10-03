package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAssociateSubmissionRequest(@NotBlank String content) {
}