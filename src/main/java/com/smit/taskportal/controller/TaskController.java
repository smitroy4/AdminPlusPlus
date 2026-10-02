package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.AssignTaskRequest;
import com.smit.taskportal.api.dto.ClientDto;
import com.smit.taskportal.api.dto.CreateTaskRequest;
import com.smit.taskportal.api.dto.TaskDetailDto;
import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.api.dto.UpdateStatusRequest;
import com.smit.taskportal.api.dto.UpdateTaskRequest;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import com.smit.taskportal.service.TaskMessageService;
import com.smit.taskportal.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskService taskService;
    private final TaskMessageService taskMessageService;
    private final CurrentUserHolder currentUser;

    public TaskController(TaskService taskService,
                          TaskMessageService taskMessageService,
                          CurrentUserHolder currentUser) {
        this.taskService = taskService;
        this.taskMessageService = taskMessageService;
        this.currentUser = currentUser;
    }

    /** Task header plus the message thread the caller may see. */
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailDto> getTask(@PathVariable Long id) {
        AppUserPrincipal actor = currentUser.require();
        Task task = taskService.getVisibleTask(id);

        TaskDto dto = TaskDto.from(task);
        var messages = taskMessageService.getVisibleMessages(task, actor);
        long total = taskMessageService.countVisibleMessages(task, actor);
        // Contact block is reserved for managers and admins.
        ClientDto clientDetails = actor.isManagerOrAbove()
                ? ClientDto.detailed(task.getClient())
                : null;

        return ApiResponse.ok(new TaskDetailDto(dto, messages, total, actor.isManagerOrAbove(), clientDetails));
    }

    /** Managers and admins only - coordinators and associates cannot raise tasks. */
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TaskDto>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskDto created = taskService.createTask(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.ok("Task created", created));
    }

    /** Manager / creator only - title, description and priority. */
    @PutMapping("/{id}")
    public ApiResponse<TaskDto> updateTask(@PathVariable Long id,
                                            @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.ok("Task updated", taskService.updateTask(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TaskDto> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok("Status updated to " + request.status(), taskService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<TaskDto> assignTask(@PathVariable Long id,
                                           @Valid @RequestBody AssignTaskRequest request) {
        return ApiResponse.ok("Task assigned", taskService.assignTask(id, request.userId()));
    }

    /** Releases the task back to the pool. MANAGER / ADMIN only. */
    @DeleteMapping("/{id}/assign")
    public ApiResponse<TaskDto> unassignTask(@PathVariable Long id) {
        return ApiResponse.ok("Task unassigned", taskService.unassignTask(id));
    }
}
