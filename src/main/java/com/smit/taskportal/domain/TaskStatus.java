package com.smit.taskportal.domain;

/**
 * Life-cycle of a task.
 *
 * <pre>
 *   OPEN ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ CLOSED
 *     ▲            │              │           │
 *     └────────────┴──────────────┴───────────┘   (re-open / re-work)
 * </pre>
 */
public enum TaskStatus {

    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CLOSED;

    /** Active work — everything that is neither finished nor archived. */
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CLOSED;
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
            case IN_PROGRESS -> target == OPEN || target == COMPLETED || target == CLOSED;
            case COMPLETED -> target == IN_PROGRESS || target == CLOSED || target == OPEN;
            case CLOSED -> target == OPEN;
        };
    }
}
