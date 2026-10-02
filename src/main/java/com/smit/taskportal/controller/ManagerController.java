package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.api.dto.UserSummaryDto;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.service.TaskService;
import com.smit.taskportal.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manager-only endpoints. Access is already enforced by the URL rules in
 * {@code SecurityConfig} ({@code /api/manager/**}); {@code @PreAuthorize} is a
 * second, defence-in-depth layer.
 */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ManagerController {

    private final TaskService taskService;
    private final UserService userService;

    public ManagerController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    /** Users that can be picked in the "assign to" dropdown (client accounts excluded). */
    @GetMapping("/users")
    public ApiResponse<List<UserSummaryDto>> assignableUsers() {
        return ApiResponse.ok(userService.findAll().stream()
                .filter(user -> user.getRole() != Role.CLIENT)
                .map(UserSummaryDto::from)
                .toList());
    }

    /** Filtered backlog. Every parameter is optional. */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskDto>> searchTasks(@RequestParam(required = false) TaskStatus status,
                                                   @RequestParam(required = false) TaskPriority priority,
                                                   @RequestParam(required = false) Long assignedToId,
                                                   @RequestParam(defaultValue = "false") boolean unassignedOnly,
                                                   @RequestParam(defaultValue = "true") boolean openOnly) {
        return ApiResponse.ok(taskService.search(status, priority, assignedToId, unassignedOnly, openOnly));
    }
}
