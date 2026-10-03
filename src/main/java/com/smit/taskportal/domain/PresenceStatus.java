package com.smit.taskportal.domain;

/**
 * Operational presence of a staff member in the operational sheets.
 *
 * <p>Derived from real task data, never configured: a member is
 * {@link #ACTIVE} while they own a task in {@link TaskStatus#IN_PROGRESS}, and
 * {@link #IDLE} otherwise (no current task at all).
 */
public enum PresenceStatus {

    /** Working a task right now — the sheet shows it. */
    ACTIVE,

    /** No task in progress. */
    IDLE
}
