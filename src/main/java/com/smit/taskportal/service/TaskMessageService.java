package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.TaskMessageDto;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskMessage;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.ForbiddenException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.TaskMessageRepository;
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
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final CurrentUserHolder currentUser;

    public TaskMessageService(TaskMessageRepository taskMessageRepository,
                              UserRepository userRepository,
                              TaskService taskService,
                              CurrentUserHolder currentUser) {
        this.taskMessageRepository = taskMessageRepository;
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
        return taskMessageRepository.countByTaskId(task.getId());
    }

    @Transactional
    public TaskMessageDto addMessage(Long taskId, String body, boolean internal) {
        AppUserPrincipal actor = currentUser.require();
        Task task = taskService.getVisibleTask(taskId);

        if (actor.isClient()) {
            throw new ForbiddenException("Client accounts have read-only access to tasks");
        }
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

        return TaskMessageDto.from(taskMessageRepository.save(message));
    }

    /** Authors can retract their own comment; admins can remove anything. */
    @Transactional
    public void deleteMessage(Long taskId, Long messageId) {
        AppUserPrincipal actor = currentUser.require();
        taskService.getVisibleTask(taskId);

        TaskMessage message = taskMessageRepository.findByIdAndTaskId(messageId, taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));

        boolean isAuthor = message.getFromUser() != null && message.getFromUser().getId().equals(actor.id());
        if (!isAuthor && !actor.isManagerOrAbove()) {
            throw new ForbiddenException("You can only delete your own messages");
        }

        taskMessageRepository.delete(message);
    }
}
