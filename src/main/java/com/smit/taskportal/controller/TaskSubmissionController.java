package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.AssociateSubmissionDto;
import com.smit.taskportal.api.dto.CreateAssociateSubmissionRequest;
import com.smit.taskportal.api.dto.ReviewSubmissionRequest;
import com.smit.taskportal.api.dto.TaskMessageDto;
import com.smit.taskportal.service.AssociateSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task/{taskId}")
public class TaskSubmissionController {

    private final AssociateSubmissionService submissionService;

    public TaskSubmissionController(AssociateSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/associate-submissions")
    public ApiResponse<List<AssociateSubmissionDto>> getSubmissions(@PathVariable Long taskId) {
        return ApiResponse.ok(submissionService.getSubmissions(taskId));
    }

    @PostMapping("/associate-submission")
    public ResponseEntity<ApiResponse<AssociateSubmissionDto>> createSubmission(@PathVariable Long taskId,
                                                                                 @Valid @RequestBody CreateAssociateSubmissionRequest request) {
        AssociateSubmissionDto created = submissionService.createSubmission(taskId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Submission created", created));
    }

    @PostMapping("/associate-submission/{submissionId}/review")
    public ApiResponse<TaskMessageDto> reviewSubmission(@PathVariable Long taskId,
                                                        @PathVariable Long submissionId,
                                                        @Valid @RequestBody ReviewSubmissionRequest request) {
        return ApiResponse.ok("Submission reviewed", submissionService.reviewSubmission(taskId, submissionId, request.response()));
    }
}