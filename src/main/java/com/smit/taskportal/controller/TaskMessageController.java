package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.CreateMessageRequest;
import com.smit.taskportal.api.dto.TaskMessageDto;
import com.smit.taskportal.service.TaskMessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task/{taskId}")
public class TaskMessageController {

    private final TaskMessageService taskMessageService;

    public TaskMessageController(TaskMessageService taskMessageService) {
        this.taskMessageService = taskMessageService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<TaskMessageDto>> getMessages(@PathVariable Long taskId) {
        return ApiResponse.ok(taskMessageService.getMessagesByTask(taskId));
    }

    @PostMapping("/message")
    public ResponseEntity<ApiResponse<TaskMessageDto>> addMessage(@PathVariable Long taskId,
                                                                  @Valid @RequestBody CreateMessageRequest request) {
        TaskMessageDto message = taskMessageService.addMessage(taskId, request.messageBody(), request.internalNote());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Message posted", message));
    }

    @DeleteMapping("/message/{messageId}")
    public ApiResponse<Void> deleteMessage(@PathVariable Long taskId, @PathVariable Long messageId) {
        taskMessageService.deleteMessage(taskId, messageId);
        return ApiResponse.ok("Message deleted");
    }
}
