package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.NotificationDto;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskMessage;
import com.smit.taskportal.repository.TaskMessageRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Live activity feed behind the navbar bell.
 *
 * <p>Three event kinds, all filtered to what the caller is allowed to see:
 * <ul>
 *   <li>{@code ASSIGNED} — a task assigned to you;</li>
 *   <li>{@code MESSAGE} — a new thread message on a task you created, were
 *       assigned, or (for clients) that belongs to your customer;</li>
 *   <li>{@code ESCALATION} — a new escalation message: every one for managers
 *       and admins, only your own for clients.</li>
 * </ul>
 *
 * <p>Read state is deliberately not stored server side — the browser keeps the
 * set of read ids in localStorage and derives the badge from it. Messages the
 * caller wrote themself are never events about them and are dropped.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int LIMIT = 20;
    private static final Duration WINDOW = Duration.ofDays(90);
    private static final int SNIPPET_LENGTH = 140;

    private final TaskRepository taskRepository;
    private final TaskMessageRepository taskMessageRepository;
    private final CurrentUserHolder currentUser;

    public NotificationService(TaskRepository taskRepository,
                               TaskMessageRepository taskMessageRepository,
                               CurrentUserHolder currentUser) {
        this.taskRepository = taskRepository;
        this.taskMessageRepository = taskMessageRepository;
        this.currentUser = currentUser;
    }

    public List<NotificationDto> getNotifications() {
        AppUserPrincipal actor = currentUser.require();
        Instant since = Instant.now().minus(WINDOW);
        List<NotificationDto> items = new ArrayList<>();

        for (Task task : taskRepository.findRecentTasksAssignedTo(actor.id(), since)) {
            items.add(new NotificationDto(
                    "assigned-" + task.getId(),
                    "ASSIGNED",
                    task.getId(),
                    task.getTaskNo(),
                    task.getTitle(),
                    "New task assigned to you",
                    null,
                    task.getCreatedBy() == null ? null : task.getCreatedBy().getUsername(),
                    task.getUpdatedAt()));
        }

        addEscalationNotifications(actor, since, items);
        addMessageNotifications(actor, since, items);

        items.sort(Comparator.comparing(NotificationDto::createdAt,
                Comparator.nullsLast(Comparator.<Instant>reverseOrder())));
        return items.stream().limit(LIMIT).toList();
    }

    /** Escalations: managers and admins see all, a client only their own. */
    private void addEscalationNotifications(AppUserPrincipal actor, Instant since,
                                            List<NotificationDto> items) {
        if (!actor.isManagerOrAbove() && !actor.isClient()) {
            return;
        }
        for (TaskMessage message : taskMessageRepository.findRecentEscalations(since)) {
            Task task = message.getTask();
            if (actor.isClient()) {
                if (task.getEscalatedBy() == null || !actor.id().equals(task.getEscalatedBy().getId())) {
                    continue;
                }
            }
            if (isOwnMessage(message, actor)) {
                continue;
            }
            items.add(messageItem("escalation-", "ESCALATION", message,
                    "New message in the escalation conversation"));
        }
    }

    private void addMessageNotifications(AppUserPrincipal actor, Instant since,
                                         List<NotificationDto> items) {
        List<TaskMessage> messages;
        if (actor.isClient()) {
            messages = actor.clientId() == null
                    ? List.of()
                    : taskMessageRepository.findRecentClientMessages(actor.clientId(), since);
        } else {
            messages = taskMessageRepository.findRecentStaffMessages(actor.id(), since);
        }
        for (TaskMessage message : messages) {
            if (isOwnMessage(message, actor)) {
                continue;
            }
            items.add(messageItem("message-", "MESSAGE", message,
                    "New message in the conversation"));
        }
    }

    private static boolean isOwnMessage(TaskMessage message, AppUserPrincipal actor) {
        return message.getFromUser() != null && actor.id().equals(message.getFromUser().getId());
    }

    private static NotificationDto messageItem(String prefix, String type, TaskMessage message,
                                               String summary) {
        Task task = message.getTask();
        return new NotificationDto(
                prefix + message.getId(),
                type,
                task.getId(),
                task.getTaskNo(),
                task.getTitle(),
                summary,
                snippet(message.getMessageBody()),
                message.getFromUser() == null ? null : message.getFromUser().getUsername(),
                message.getCreatedAt());
    }

    private static String snippet(String body) {
        if (body == null) {
            return null;
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH) + "…";
    }
}
