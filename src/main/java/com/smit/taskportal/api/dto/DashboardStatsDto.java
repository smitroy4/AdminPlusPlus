package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;

import java.util.List;

/**
 * Aggregated counters for the dashboard tiles and the two charts.
 * {@code teamXxx} counters are populated for COORDINATOR and above (the
 * workload of ASSOCIATE accounts), {@code allXxx} counters only for
 * MANAGER and ADMIN (the company-wide backlog).
 *
 * <p>{@code myXxx} counts what is assigned to the caller — except for CLIENT
 * accounts, which are never assignees and instead count the tasks of their own
 * customer (see {@code TaskService.getDashboardStats()}).
 */
public record DashboardStatsDto(long myOpen,
                                long myInProgress,
                                long myQuality,
                                long mySubmitted,
                                long myTotal,
                                long teamOpen,
                                long teamInProgress,
                                long teamQuality,
                                long teamSubmitted,
                                long teamTotal,
                                long allOpen,
                                long allInProgress,
                                long allQuality,
                                long allSubmitted,
                                long allTotal,
                                boolean canViewTeam,
                                boolean canViewAll,
                                List<Bucket> byStatus,
                                List<Bucket> byPriority) {

    public record Bucket(String label, long count) {
    }

    public static Bucket statusBucket(TaskStatus status, long count) {
        return new Bucket(status.name(), count);
    }

    public static Bucket priorityBucket(TaskPriority priority, long count) {
        return new Bucket(priority.name(), count);
    }
}
