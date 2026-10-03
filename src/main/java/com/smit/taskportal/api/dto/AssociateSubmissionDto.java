package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.AssociateSubmission;
import com.smit.taskportal.domain.AssociateSubmissionStatus;

import java.time.Instant;

public record AssociateSubmissionDto(Long id,
                                    Long taskId,
                                    UserSummaryDto employee,
                                    String content,
                                    AssociateSubmissionStatus status,
                                    UserSummaryDto reviewedBy,
                                    Instant reviewedAt,
                                    Instant createdAt) {

    public static AssociateSubmissionDto from(AssociateSubmission submission) {
        return new AssociateSubmissionDto(
                submission.getId(),
                submission.getTask() == null ? null : submission.getTask().getId(),
                UserSummaryDto.from(submission.getEmployee()),
                submission.getContent(),
                submission.getStatus(),
                UserSummaryDto.from(submission.getReviewedBy()),
                submission.getReviewedAt(),
                submission.getCreatedAt());
    }
}