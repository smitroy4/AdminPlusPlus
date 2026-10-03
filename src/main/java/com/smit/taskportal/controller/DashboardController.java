package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.DashboardStatsDto;
import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.api.dto.TeamMemberStatusDto;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.service.TaskService;
import com.smit.taskportal.service.TeamService;
import com.smit.taskportal.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final TaskService taskService;
    private final UserService userService;
    private final TeamService teamService;

    public DashboardController(TaskService taskService, UserService userService, TeamService teamService) {
        this.taskService = taskService;
        this.userService = userService;
        this.teamService = teamService;
    }

    /** Tile counters + status/priority breakdowns for the current user. */
    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStatsDto> stats() {
        return ApiResponse.ok(taskService.getDashboardStats());
    }

    /**
     * Operational sheet for one staff role (MANAGER, COORDINATOR or ASSOCIATE):
     * who is active, on which task, and since when. Which sheets a caller may
     * read is decided in {@link TeamService} — associates and clients get a 403.
     */
    @GetMapping("/dashboard/team")
    public ApiResponse<List<TeamMemberStatusDto>> teamSheet(@RequestParam Role role) {
        return ApiResponse.ok(teamService.getSheet(role));
    }

    /** Active tasks "mine": assigned to me, or for client accounts of my customer. */
    @GetMapping("/tasks/my-open")
    public ApiResponse<List<TaskDto>> myOpenTasks() {
        return ApiResponse.ok(taskService.getMyOpenTasks());
    }

    /** Everything I raised and not finished yet. */
    @GetMapping("/tasks/my-created")
    public ApiResponse<List<TaskDto>> myCreatedTasks() {
        return ApiResponse.ok(taskService.getTasksByCreator(userService.getCurrentUser(), true));
    }

    /** Every task I am involved in, regardless of status. */
    @GetMapping("/tasks/mine")
    public ApiResponse<List<TaskDto>> myTasks() {
        var me = userService.getCurrentUser();
        List<TaskDto> assigned = taskService.getTasksByAssignedUser(me, false);
        return ApiResponse.ok(assigned);
    }

    /**
     * Every active task in the portal. Open to all authenticated roles; client
     * accounts receive only their own customer's tasks (scoped in the service).
     */
    @GetMapping("/tasks/all-open")
    public ApiResponse<List<TaskDto>> allOpenTasks() {
        return ApiResponse.ok(taskService.getAllActiveTasks());
    }

    /** Every task regardless of status. Same visibility rules as {@code /tasks/all-open}. */
    @GetMapping("/tasks/all")
    public ApiResponse<List<TaskDto>> allTasks() {
        return ApiResponse.ok(taskService.getAllTasks());
    }

    /** Tasks filtered by a single status value. */
    @GetMapping("/tasks/by-status")
    public ApiResponse<List<TaskDto>> byStatus(@RequestParam TaskStatus status) {
        return ApiResponse.ok(taskService.getTasksByStatus(status));
    }
}
