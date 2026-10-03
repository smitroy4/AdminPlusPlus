package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.TaskMessageDto;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskMessage;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.BadRequestException;
import com.smit.taskportal.exception.ForbiddenException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.TaskMessageRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskMessageService {

    private final TaskMessageRepository taskMessageRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final CurrentUserHolder currentUser;

    public TaskMessageService(TaskMessageRepository taskMessageRepository,
                              TaskRepository taskRepository,
                              UserRepository userRepository,
                              TaskService taskService,
                              CurrentUserHolder currentUser) {
        this.taskMessageRepository = taskMessageRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    /**
     * The thread as the caller is allowed to see it.
     *
     * <p>Internal notes are reserved for managers/admins plus the author of the
     * note itself.
     */
    public List<TaskMessageDto> getMessagesByTask(Long taskId) {
        AppUserPrincipal actor = currentUser.require();
        Task task = taskService.getVisibleTask(taskId);

        List<TaskMessage> messages = actor.isManagerOrAbove()
                ? taskMessageRepository.findByTaskId(taskId)
                : taskMessageRepository.findVisibleByTaskId(taskId, actor.id());

        return messages.stream().map(TaskMessageDto::from).toList();
    }

    public List<TaskMessageDto> getVisibleMessages(Task task, AppUserPrincipal actor) {
        List<TaskMessage> messages = actor.isManagerOrAbove()
                ? taskMessageRepository.findByTaskId(task.getId())
                : taskMessageRepository.findVisibleByTaskId(task.getId(), actor.id());
        return messages.stream().map(TaskMessageDto::from).toList();
    }

    public long countVisibleMessages(Task task, AppUserPrincipal actor) {
        return taskMessageRepository.countByTaskIdAndEscalationFalse(task.getId());
    }

    /**
     * The escalation conversation of a task, or an empty list when the caller
     * is not part of it. Participants are managers/admins and the client who
     * escalated the task.
     */
    public List<TaskMessageDto> getEscalations(Task task, AppUserPrincipal actor) {
        if (!isEscalationParticipant(actor, task)) {
            return List.of();
        }
        return taskMessageRepository.findEscalationsByTaskId(task.getId())
                .stream().map(TaskMessageDto::from).toList();
    }

    /** Managers/admins are always participants; clients only after they escalated. */
    public static boolean isEscalationParticipant(AppUserPrincipal actor, Task task) {
        if (actor.isManagerOrAbove()) {
            return true;
        }
        return task.getEscalatedBy() != null && task.getEscalatedBy().getId().equals(actor.id());
    }

    /**
     * Adds one entry to the escalation conversation.
     *
     * <ul>
     *   <li>CLIENT — opens the conversation (task is flagged escalated and its
     *       priority raised to URGENT), or continues it when they already
     *       escalated this task.</li>
     *   <li>MANAGER / ADMIN — replies, but only once an escalation exists.</li>
     *   <li>everybody else — forbidden; associates and coordinators never see
     *       this conversation.</li>
     * </ul>
     */
    @Transactional
    public TaskMessageDto escalate(Long taskId, String body) {
        AppUserPrincipal actor = currentUser.require();
        Task task = taskService.getVisibleTask(taskId);
        User author = userRepository.findById(actor.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.id()));

        if (actor.isManagerOrAbove()) {
            if (!task.isEscalated()) {
                throw new BadRequestException("There is no escalation conversation on this task yet");
            }
        } else if (actor.isClient()) {
            if (task.isEscalated() && !task.getEscalatedBy().getId().equals(actor.id())) {
                throw new ForbiddenException("This task has already been escalated by another user");
            }
            boolean first = !task.isEscalated();
            if (first) {
                task.setEscalatedBy(author);
            }
            if (!task.getPriority().isAtLeast(TaskPriority.URGENT)) {
                task.setPriority(TaskPriority.URGENT);
            }
            taskRepository.save(task);
        } else {
            throw new ForbiddenException("Only client accounts can escalate a task to a manager");
        }

        TaskMessage message = TaskMessage.builder()
                .messageBody(body)
                .escalation(true)
                .fromUser(author)
                .build();
        task.addMessage(message);
        task.touch();

        return TaskMessageDto.from(taskMessageRepository.save(message));
    }

    /**
     * Adds one entry to the shared thread. Client accounts use this too — their
     * private channel is {@link #escalate}, reached from the same composer by
     * ticking "Escalate to Manager".
     *
     * <p>Associates never write straight into the conversation: their update is
     * captured as an associate submission for the reviewers instead (see
     * {@code AssociateSubmissionService#createSubmission}), so this endpoint
     * refuses them.
     *
     * <p>Workflow: when a coordinator/manager/admin replies to a task that is
     * waiting in QUALITY, the task is automatically moved on to SUBMITTED.
     */
    @Transactional
    public TaskMessageDto addMessage(Long taskId, String body, boolean internal) {
        AppUserPrincipal actor = currentUser.require();
        if (actor.isAssociate()) {
            throw new ForbiddenException(
                    "Associates submit updates for review; post it as a submission instead of a conversation message");
        }
        Task task = taskService.getVisibleTask(taskId);

        if (internal && !actor.isManagerOrAbove()) {
            throw new ForbiddenException("Only managers and admins can add internal notes");
        }

        User author = userRepository.findById(actor.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.id()));

        TaskMessage message = TaskMessage.builder()
                .messageBody(body)
                .internal(internal)
                .fromUser(author)
                .build();
        task.addMessage(message);
        task.touch();
        TaskMessage saved = taskMessageRepository.save(message);

        if (actor.role().isCoordinatorOrAbove() && task.getStatus() == TaskStatus.QUALITY) {
            /* Stamp after the reply is written, so a status move clears
               everything reported about this task — including its own. */
            task.changeStatus(TaskStatus.SUBMITTED);
        }

        return TaskMessageDto.from(saved);
    }

    /** Only ADMIN can delete messages. */
    @Transactional
    public void deleteMessage(Long taskId, Long messageId) {
        AppUserPrincipal actor = currentUser.require();
        taskService.getVisibleTask(taskId);

        TaskMessage message = taskMessageRepository.findByIdAndTaskId(messageId, taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));

        if (!actor.isAdmin()) {
            throw new ForbiddenException("Only administrators can delete messages");
        }

        taskMessageRepository.delete(message);
    }
}
