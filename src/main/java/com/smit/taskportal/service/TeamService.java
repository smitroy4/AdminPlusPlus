package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.api.dto.TeamMemberStatusDto;
import com.smit.taskportal.api.dto.UserSummaryDto;
import com.smit.taskportal.domain.PresenceStatus;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.ForbiddenException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operational sheets for the dashboard: who is working on what, right now.
 *
 * <p>Nothing here is configured or cached — a member is {@code ACTIVE} exactly
 * while they own an {@code IN_PROGRESS} task, and the "current task" is their
 * most recently updated one. Everything else is {@code IDLE} with no task and no
 * timestamp, which is what the sheets render as an em dash.
 *
 * <p><b>Authorisation.</b> The portal has no reporting-line graph, so "the people
 * a manager may see" resolves to the roles that role is responsible for:
 *
 * <ul>
 *   <li>ADMIN — any staff sheet (MANAGER, COORDINATOR, ASSOCIATE)</li>
 *   <li>MANAGER — coordinators and associates</li>
 *   <li>COORDINATOR — associates</li>
 *   <li>ASSOCIATE / CLIENT — no sheet at all</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TeamService {

    /** What each role is allowed to look at. Purely a function of the caller. */
    private static final Map<Role, Set<Role>> VISIBLE_SHEETS = Map.of(
            Role.ADMIN, EnumSet.of(Role.MANAGER, Role.COORDINATOR, Role.ASSOCIATE),
            Role.MANAGER, EnumSet.of(Role.COORDINATOR, Role.ASSOCIATE),
            Role.COORDINATOR, EnumSet.of(Role.ASSOCIATE));

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CurrentUserHolder currentUser;

    public TeamService(UserRepository userRepository,
                       TaskRepository taskRepository,
                       CurrentUserHolder currentUser) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.currentUser = currentUser;
    }

    /**
     * The sheet for one staff role.
     *
     * @throws ForbiddenException      when the caller may not see that role's sheet
     * @throws ResourceNotFoundException when {@code role} is not a staff role
     */
    public List<TeamMemberStatusDto> getSheet(Role role) {
        AppUserPrincipal actor = currentUser.require();
        Set<Role> allowed = VISIBLE_SHEETS.getOrDefault(actor.role(), Set.of());
        if (!isStaffRole(role) || !allowed.contains(role)) {
            throw new ForbiddenException("You are not allowed to view the %s sheet".formatted(role));
        }

        List<User> members = userRepository.findAllByRoleInOrderByUsernameAsc(List.of(role));
        if (members.isEmpty()) {
            return List.of();
        }

        Map<Long, Task> currentTaskByAssignee = currentTasksByAssignee(members);
        return members.stream()
                .map(member -> toRow(member, currentTaskByAssignee.get(member.getId())))
                .toList();
    }

    /** The most recently updated in-progress task per member, in one query. */
    private Map<Long, Task> currentTasksByAssignee(List<User> members) {
        List<Long> ids = members.stream().map(User::getId).toList();
        Map<Long, Task> byAssignee = new LinkedHashMap<>();
        for (Task task : taskRepository
                .findByAssignedToIdInAndStatusInOrderByUpdatedAtDesc(ids, List.of(TaskStatus.IN_PROGRESS))) {
            /* The query is ordered by updatedAt desc, so the first hit per
               assignee is that member's current task. */
            byAssignee.putIfAbsent(task.getAssignedTo().getId(), task);
        }
        return byAssignee;
    }

    private static TeamMemberStatusDto toRow(User member, Task currentTask) {
        UserSummaryDto user = UserSummaryDto.from(member);
        if (currentTask == null) {
            return new TeamMemberStatusDto(user, member.getRole(), PresenceStatus.IDLE, null, null);
        }
        TaskDto task = TaskDto.from(currentTask);
        return new TeamMemberStatusDto(user, member.getRole(), PresenceStatus.ACTIVE, task,
                currentTask.getUpdatedAt());
    }

    private static boolean isStaffRole(Role role) {
        return role == Role.MANAGER || role == Role.COORDINATOR || role == Role.ASSOCIATE;
    }
}
