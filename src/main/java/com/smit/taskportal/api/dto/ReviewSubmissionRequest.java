package com.smit.taskportal.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewSubmissionRequest(@NotBlank String response) {
}