package com.smit.taskportal.api.dto;

import java.util.List;

/**
 * Payload of {@code GET /api/clients/{id}/profile} — the Client Details page.
 *
 * <p>{@code client} is shaped by the caller's role (see {@link ClientDto}), and
 * both {@code taskStats} and {@code recentTasks} are computed from the subset of
 * the customer's tasks the caller is allowed to see — so an associate only ever
 * sees the numbers behind the tasks they can already open.
 */
public record ClientProfileDto(ClientDto client,
                               TaskStats taskStats,
                               List<TaskDto> recentTasks) {

    /** Task counters for the customer, scoped to what the caller may see. */
    public record TaskStats(long total,
                            long open,
                            long inProgress,
                            long completed,
                            long closed) {
    }
}
