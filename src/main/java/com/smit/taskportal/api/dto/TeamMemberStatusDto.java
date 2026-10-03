package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.PresenceStatus;
import com.smit.taskportal.domain.Role;

import java.time.Instant;

/**
 * One row of an operational sheet (Managers / Coordinators / Associates).
 *
 * <p>{@code presence} and {@code currentTask} are computed from live task data
 * by {@code TeamService}: {@code currentTask} is the member's most recently
 * touched {@code IN_PROGRESS} task, {@code since} is that task's last update.
 * An idle member carries no task and no timestamp.
 */
public record TeamMemberStatusDto(UserSummaryDto user,
                                  Role role,
                                  PresenceStatus presence,
                                  TaskDto currentTask,
                                  Instant since) {
}
