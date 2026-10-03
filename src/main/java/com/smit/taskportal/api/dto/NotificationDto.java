package com.smit.taskportal.api.dto;

import java.time.Instant;

/**
 * One row of the live activity feed behind the navbar bell.
 *
 * <p>{@code id} is derived from a primary key ({@code assigned-7},
 * {@code message-42}, {@code escalation-43}) so it stays stable across reloads —
 * the browser keeps the set of read ids in localStorage to compute the badge.
 *
 * @param type    {@code ASSIGNED}, {@code MESSAGE} or {@code ESCALATION}
 * @param summary fixed, human readable line describing the event
 * @param snippet trimmed copy of the message body, when the event carries one
 */
public record NotificationDto(String id,
                              String type,
                              Long taskId,
                              String taskNo,
                              String taskTitle,
                              String summary,
                              String snippet,
                              String actor,
                              Instant createdAt) {
}
