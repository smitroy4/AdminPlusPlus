package com.smit.taskportal.domain;

/**
 * Life-cycle of a task.
 *
 * <pre>
 *   OPEN ──▶ IN_PROGRESS ──▶ QUALITY ──▶ SUBMITTED ──▶ CLOSED
 *     ▲            │             │            │           │
 *     └────────────┴─────────────┴────────────┴───────────┘   (re-open / re-work)
 * </pre>
 *
 * <p>QUALITY and SUBMITTED are also driven by the review workflow rather than
 * by hand: an associate's update moves the task to {@link #QUALITY}, and the
 * coordinator/manager/admin who reviews it moves it to {@link #SUBMITTED}.
 */
public enum TaskStatus {

    OPEN,
    IN_PROGRESS,
    QUALITY,
    SUBMITTED,
    CLOSED;

    /** Active work — everything that is neither finished nor archived. */
    public boolean isActive() {
        return this != CLOSED;
    }

    public boolean isFinal() {
        return this == CLOSED;
    }

    /**
     * Allowed forward/backward moves. Re-opening a CLOSED task is permitted at
     * the domain level but is additionally restricted to managers by
     * {@code TaskService#updateStatus}.
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == CLOSED;
            case IN_PROGRESS -> target == OPEN || target == QUALITY || target == CLOSED;
            case QUALITY -> target == IN_PROGRESS || target == SUBMITTED || target == CLOSED || target == OPEN;
            case SUBMITTED -> target == IN_PROGRESS || target == QUALITY || target == CLOSED || target == OPEN;
            case CLOSED -> target == OPEN;
        };
    }
}
