package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.CreateTaskRequest;
import com.smit.taskportal.api.dto.DashboardStatsDto;
import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.api.dto.UpdateTaskRequest;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskMessage;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.BadRequestException;
import com.smit.taskportal.exception.ForbiddenException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.ClientRepository;
import com.smit.taskportal.repository.TaskMessageRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS);
    private static final List<TaskStatus> ALL_STATUSES = List.of(TaskStatus.values());
    private static final List<TaskPriority> ALL_PRIORITIES = List.of(TaskPriority.values());

    private final TaskRepository taskRepository;
    private final TaskMessageRepository taskMessageRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final TaskNoGenerator taskNoGenerator;
    private final CurrentUserHolder currentUser;

    public TaskService(TaskRepository taskRepository,
                       TaskMessageRepository taskMessageRepository,
                       UserRepository userRepository,
                       ClientRepository clientRepository,
                       TaskNoGenerator taskNoGenerator,
                       CurrentUserHolder currentUser) {
        this.taskRepository = taskRepository;
        this.taskMessageRepository = taskMessageRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.taskNoGenerator = taskNoGenerator;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ reads

    /** @throws ResourceNotFoundException when the task does not exist. */
    public Task getTaskEntity(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
    }

    /**
     * Loads a task and asserts the caller may see it.
     *
     * <p>Visibility rule: managers and admins see everything; client accounts
     * see only the tasks belonging to their own customer; everybody else sees
     * only the tasks they created or are assigned to.
     */
    public Task getVisibleTask(Long taskId) {
        Task task = getTaskEntity(taskId);
        if (!canView(currentUser.require(), task)) {
            throw new ForbiddenException("You are not allowed to view task " + task.getTaskNo());
        }
        return task;
    }

    /** Active work items assigned to the caller, newest first. */
    public List<TaskDto> getTasksByAssignedUser(User user, boolean activeOnly) {
        List<Task> tasks = activeOnly
                ? taskRepository.findByAssignedToAndStatusIn(user, ACTIVE_STATUSES)
                : taskRepository.findByAssignedTo(user);
        return sortForDisplay(tasks);
    }

    /**
     * The dashboard's "My open tasks" list: active tasks assigned to the
     * caller, or — for client accounts, who are never assignees — the active
     * tasks of their own customer.
     */
    public List<TaskDto> getMyOpenTasks() {
        AppUserPrincipal actor = currentUser.require();
        if (isClientWithCustomer(actor)) {
            return sortForDisplay(taskRepository.findByClientIdAndStatusIn(actor.clientId(), ACTIVE_STATUSES));
        }
        return sortForDisplay(taskRepository.findByAssignedToAndStatusIn(getUser(actor.id()), ACTIVE_STATUSES));
    }

    public List<TaskDto> getTasksByCreator(User user, boolean activeOnly) {
        List<Task> tasks = activeOnly
                ? taskRepository.findByCreatedByIdAndStatusIn(user.getId(), ACTIVE_STATUSES)
                : taskRepository.findByCreatedBy(user);
        return sortForDisplay(tasks);
    }

    public List<TaskDto> getTasksByStatus(TaskStatus status) {
        return sortForDisplay(taskRepository.findByStatus(status));
    }

    public List<TaskDto> getTasksByStatusIn(List<TaskStatus> statuses) {
        return sortForDisplay(taskRepository.findByStatusInOrderByCreatedAtDesc(statuses));
    }

    /** Active work items across the whole portal; every authenticated role (client accounts see only their own customer's tasks). */
    public List<TaskDto> getAllActiveTasks() {
        return sortForDisplay(visibleToCaller(taskRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)));
    }

    /** Every task regardless of status; same visibility rules as {@link #getAllActiveTasks()}. */
    public List<TaskDto> getAllTasks() {
        return sortForDisplay(visibleToCaller(taskRepository.findAll()));
    }

    public List<TaskDto> search(TaskStatus status, TaskPriority priority, Long assignedToId,
                                boolean unassignedOnly, boolean openOnly) {
        return sortForDisplay(taskRepository.search(status, priority, assignedToId,
                unassignedOnly, openOnly, ACTIVE_STATUSES));
    }

    public TaskDto getTaskDetail(Long taskId) {
        return TaskDto.from(getVisibleTask(taskId));
    }

    public long countMessages(Long taskId) {
        return taskMessageRepository.countByTaskId(taskId);
    }

    // ----------------------------------------------------------------- writes

    /**
     * Creates a task, optionally assigning it in the same call.
     *
     * <p>Two kinds of caller may raise work:
     *
     * <ul>
     *   <li><b>MANAGER / ADMIN</b> — full control: they pick the customer and may
     *       assign an agent straight away.</li>
     *   <li><b>CLIENT</b> — may open a ticket against <em>their own</em> customer
     *       only, and may never nominate an agent: the task lands unassigned in the
     *       backlog for the internal team to pick up. Both restrictions are checked
     *       here, so they hold no matter what the UI sends.</li>
     * </ul>
     */
    @Transactional
    public TaskDto createTask(CreateTaskRequest request) {
        AppUserPrincipal actor = currentUser.require();
        boolean raisedByClient = actor.isClient();
        if (!raisedByClient && !actor.isManagerOrAbove()) {
            throw new ForbiddenException("Only a manager, admin or client can create a task");
        }

        Task task = Task.builder()
                .taskNo(taskNoGenerator.next())
                .title(request.title().strip())
                .description(request.description())
                .priority(request.priority())
                .status(TaskStatus.OPEN)
                .createdBy(getUser(actor.id()))
                .build();

        if (raisedByClient) {
            applyClientRules(request, actor, task);
        } else {
            if (request.assignedToId() != null) {
                task.setAssignedTo(getUser(request.assignedToId()));
            }
            if (request.clientId() != null) {
                task.setClient(clientRepository.findById(request.clientId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Client", request.clientId())));
            }
        }

        Task saved = taskRepository.save(task);

        // The description is the first entry of the conversation thread.
        if (saved.getDescription() != null && !saved.getDescription().isBlank()) {
            TaskMessage opening = TaskMessage.builder()
                    .messageBody(saved.getDescription())
                    .internal(false)
                    .fromUser(saved.getCreatedBy())
                    .build();
            saved.addMessage(opening);
            saved = taskRepository.save(saved);
        }

        return TaskDto.from(saved);
    }

    /**
     * A client always files against its own customer and never assigns an agent.
     * Rejecting (rather than silently ignoring) keeps the rule visible instead of
     * letting a caller believe it picked an assignee.
     */
    private void applyClientRules(CreateTaskRequest request, AppUserPrincipal actor, Task task) {
        if (request.assignedToId() != null) {
            throw new ForbiddenException("Client accounts cannot assign a task to an agent");
        }
        if (actor.clientId() == null) {
            throw new BadRequestException("Your account is not linked to a client record");
        }
        if (request.clientId() != null && !request.clientId().equals(actor.clientId())) {
            throw new ForbiddenException("Client accounts can only raise tasks for their own customer");
        }
        task.setClient(clientRepository.findById(actor.clientId())
                .orElseThrow(() -> ResourceNotFoundException.of("Client", actor.clientId())));
    }

    /** Managers and admins may change title / description / priority. */
    @Transactional
    public TaskDto updateTask(Long taskId, UpdateTaskRequest request) {
        AppUserPrincipal actor = currentUser.require();
        Task task = getTaskEntity(taskId);
        requireManagerOrOwner(actor, task, "edit");

        task.setTitle(request.title().strip());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.description() != null && !request.description().strip().isBlank()
                && !request.description().strip().equals(task.getDescription())) {
            String revision = "%s edited the description:\n\n%s"
                    .formatted(actor.username(), request.description().strip());
            task.addMessage(TaskMessage.builder()
                    .messageBody(revision)
                    .internal(false)
                    .fromUser(getUser(actor.id()))
                    .build());
        }

        return TaskDto.from(taskRepository.save(task));
    }

    /**
     * Moves a task along its life-cycle.
     *
     * <p>Allowed for the assignee, the creator and any manager. Re-opening a
     * CLOSED task is reserved for managers.
     */
    @Transactional
    public TaskDto updateStatus(Long taskId, TaskStatus target) {
        AppUserPrincipal actor = currentUser.require();
        Task task = getTaskEntity(taskId);

        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(actor.id());
        boolean isCreator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(actor.id());
        if (!actor.isManagerOrAbove() && !isAssignee && !isCreator) {
            throw new ForbiddenException("Only the assignee, the creator or a manager can change this status");
        }

        TaskStatus current = task.getStatus();
        if (current == target) {
            return TaskDto.from(task);
        }
        if (!current.canTransitionTo(target)) {
            throw new BadRequestException("Cannot move a task from %s to %s".formatted(current, target));
        }
        if (current == TaskStatus.CLOSED && !actor.isManagerOrAbove()) {
            throw new ForbiddenException("Only a manager or admin can re-open a closed task");
        }

        task.setStatus(target);

        if (target.isFinal() && task.getAssignedTo() == null) {
            // Auto-close the loop by assigning the finishing owner.
            task.setAssignedTo(getUser(actor.id()));
        }

        return TaskDto.from(taskRepository.save(task));
    }

    /**
     * Assigns a task to a user.
     *
     * <ul>
     *   <li>managers/admins may assign anybody</li>
     *   <li>everybody may claim an unassigned task for themselves</li>
     *   <li>re-assigning a task that already has an owner requires a manager</li>
     * </ul>
     */
    @Transactional
    public TaskDto assignTask(Long taskId, Long assigneeId) {
        AppUserPrincipal actor = currentUser.require();
        Task task = getTaskEntity(taskId);

        if (actor.isClient()) {
            throw new ForbiddenException("Client accounts cannot reassign tasks");
        }
        boolean selfClaim = assigneeId != null && assigneeId.equals(actor.id());
        if (!actor.isManagerOrAbove() && !selfClaim) {
            throw new ForbiddenException("Only a manager or admin can assign this task to another user");
        }

        User currentAssignee = task.getAssignedTo();
        boolean alreadyOwned = currentAssignee != null;
        if (!actor.isManagerOrAbove() && alreadyOwned && !selfClaim) {
            throw new ForbiddenException("This task is already assigned to " + currentAssignee.getUsername());
        }
        if (selfClaim && !actor.isManagerOrAbove() && alreadyOwned && !currentAssignee.getId().equals(actor.id())) {
            throw new ForbiddenException("This task is already assigned to " + currentAssignee.getUsername());
        }

        task.setAssignedTo(getUser(assigneeId));
        return TaskDto.from(taskRepository.save(task));
    }

    /** Releases a task back to the unassigned pool (manager/admin only). */
    @Transactional
    public TaskDto unassignTask(Long taskId) {
        AppUserPrincipal actor = currentUser.require();
        if (!actor.isManagerOrAbove()) {
            throw new ForbiddenException("Only a manager or admin can unassign a task");
        }
        Task task = getTaskEntity(taskId);
        task.setAssignedTo(null);
        return TaskDto.from(taskRepository.save(task));
    }

    // ------------------------------------------------------------------ stats

    public DashboardStatsDto getDashboardStats() {
        AppUserPrincipal actor = currentUser.require();
        boolean canViewTeam = actor.role().isCoordinatorOrAbove();
        boolean canViewAll = actor.role().isManagerOrAbove();

        /* "My work" is assignment-based — except for client accounts, which are
           never assignees and therefore count their own customer's tasks. */
        long myOpen;
        long myInProgress;
        long myCompleted;
        long myTotal;
        if (isClientWithCustomer(actor)) {
            Long clientId = actor.clientId();
            myOpen = taskRepository.countByClientIdAndStatusIn(clientId, List.of(TaskStatus.OPEN));
            myInProgress = taskRepository.countByClientIdAndStatusIn(clientId, List.of(TaskStatus.IN_PROGRESS));
            myCompleted = taskRepository.countByClientIdAndStatusIn(clientId, List.of(TaskStatus.COMPLETED));
            myTotal = taskRepository.countByClientId(clientId);
        } else {
            myOpen = taskRepository.countByAssignedToIdAndStatusIn(actor.id(), List.of(TaskStatus.OPEN));
            myInProgress = taskRepository.countByAssignedToIdAndStatusIn(actor.id(), List.of(TaskStatus.IN_PROGRESS));
            myCompleted = taskRepository.countByAssignedToIdAndStatusIn(actor.id(), List.of(TaskStatus.COMPLETED));
            myTotal = taskRepository.countByAssignedToId(actor.id());
        }

        long teamOpen = 0;
        long teamInProgress = 0;
        long teamCompleted = 0;
        long teamTotal = 0;
        if (canViewTeam) {
            teamOpen = taskRepository.countByRoleAndStatus(Role.ASSOCIATE, TaskStatus.OPEN);
            teamInProgress = taskRepository.countByRoleAndStatus(Role.ASSOCIATE, TaskStatus.IN_PROGRESS);
            teamCompleted = taskRepository.countByRoleAndStatus(Role.ASSOCIATE, TaskStatus.COMPLETED);
            teamTotal = taskRepository.countByRole(Role.ASSOCIATE);
        }

        long allOpen = 0;
        long allInProgress = 0;
        long allCompleted = 0;
        long allTotal = 0;
        if (canViewAll) {
            allOpen = taskRepository.countByStatus(TaskStatus.OPEN);
            allInProgress = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);
            allCompleted = taskRepository.countByStatus(TaskStatus.COMPLETED);
            allTotal = taskRepository.count();
        }

        List<DashboardStatsDto.Bucket> byStatus = new ArrayList<>();
        List<DashboardStatsDto.Bucket> byPriority = new ArrayList<>();

        if (canViewAll) {
            Map<TaskStatus, Long> statusCounts = bucketBy(taskRepository.countGroupedByStatus(), TaskStatus.values());
            for (TaskStatus status : ALL_STATUSES) {
                byStatus.add(DashboardStatsDto.statusBucket(status, statusCounts.getOrDefault(status, 0L)));
            }
            Map<TaskPriority, Long> priorityCounts = bucketBy(taskRepository.countGroupedByPriority(), TaskPriority.values());
            for (TaskPriority priority : ALL_PRIORITIES) {
                byPriority.add(DashboardStatsDto.priorityBucket(priority, priorityCounts.getOrDefault(priority, 0L)));
            }
        } else if (canViewTeam) {
            Map<TaskStatus, Long> statusCounts = bucketBy(
                    taskRepository.countGroupedByStatusForRole(Role.ASSOCIATE), TaskStatus.values());
            for (TaskStatus status : ALL_STATUSES) {
                byStatus.add(DashboardStatsDto.statusBucket(status, statusCounts.getOrDefault(status, 0L)));
            }
            Map<TaskPriority, Long> priorityCounts = bucketBy(
                    taskRepository.countGroupedByPriorityForRole(Role.ASSOCIATE), TaskPriority.values());
            for (TaskPriority priority : ALL_PRIORITIES) {
                byPriority.add(DashboardStatsDto.priorityBucket(priority, priorityCounts.getOrDefault(priority, 0L)));
            }
        } else {
            List<Task> myTasks = isClientWithCustomer(actor)
                    ? taskRepository.findByClientId(actor.clientId())
                    : taskRepository.findByUserId(actor.id());
            Map<TaskStatus, Long> myStatusCounts = new EnumMap<>(TaskStatus.class);
            Map<TaskPriority, Long> myPriorityCounts = new EnumMap<>(TaskPriority.class);
            for (Task task : myTasks) {
                myStatusCounts.merge(task.getStatus(), 1L, Long::sum);
                myPriorityCounts.merge(task.getPriority(), 1L, Long::sum);
            }
            for (TaskStatus status : ALL_STATUSES) {
                byStatus.add(DashboardStatsDto.statusBucket(status, myStatusCounts.getOrDefault(status, 0L)));
            }
            for (TaskPriority priority : ALL_PRIORITIES) {
                byPriority.add(DashboardStatsDto.priorityBucket(priority, myPriorityCounts.getOrDefault(priority, 0L)));
            }
        }

        return new DashboardStatsDto(myOpen, myInProgress, myCompleted, myTotal,
                teamOpen, teamInProgress, teamCompleted, teamTotal,
                allOpen, allInProgress, allCompleted, allTotal,
                canViewTeam, canViewAll, byStatus, byPriority);
    }

    // ------------------------------------------------------------- permissions

    public static boolean canView(AppUserPrincipal actor, Task task) {
        if (actor.isClient()) {
            return task.getClient() != null && task.getClient().getId().equals(actor.clientId());
        }
        if (actor.isManagerOrAbove()) {
            return true;
        }
        if (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(actor.id())) {
            return true;
        }
        return task.getAssignedTo() != null && task.getAssignedTo().getId().equals(actor.id());
    }

    /** {@code true} for a CLIENT account that is actually linked to a customer. */
    private static boolean isClientWithCustomer(AppUserPrincipal actor) {
        return actor.isClient() && actor.clientId() != null;
    }

    private static void requireManagerOrOwner(AppUserPrincipal actor, Task task, String action) {
        if (actor.isManagerOrAbove()) {
            return;
        }
        boolean isCreator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(actor.id());
        if (!isCreator) {
            throw new ForbiddenException("Only the creator or a manager can %s this task".formatted(action));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Client accounts only ever see tasks that belong to their own customer. */
    private List<Task> visibleToCaller(List<Task> tasks) {
        AppUserPrincipal actor = currentUser.require();
        if (!actor.isClient()) {
            return tasks;
        }
        return tasks.stream()
                .filter(task -> task.getClient() != null
                        && task.getClient().getId().equals(actor.clientId()))
                .toList();
    }

    private User getUser(Long id) {
        if (id == null) {
            throw new BadRequestException("User id must not be null");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private static List<TaskDto> sortForDisplay(List<Task> tasks) {
        return tasks.stream()
                .sorted((left, right) -> {
                    int byPriority = Integer.compare(right.getPriority().getWeight(), left.getPriority().getWeight());
                    if (byPriority != 0) {
                        return byPriority;
                    }
                    return right.getCreatedAt().compareTo(left.getCreatedAt());
                })
                .map(TaskDto::from)
                .toList();
    }

    private static <E extends Enum<E>> Map<E, Long> bucketBy(List<Object[]> rows, E[] keys) {
        Map<E, Long> result = new EnumMap<>(keys[0].getDeclaringClass());
        for (Object[] row : rows) {
            @SuppressWarnings("unchecked")
            E key = (E) row[0];
            result.put(key, ((Number) row[1]).longValue());
        }
        return result;
    }
}
