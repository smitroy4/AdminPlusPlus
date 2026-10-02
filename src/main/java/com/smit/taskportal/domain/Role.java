package com.smit.taskportal.domain;

/**
 * Application roles. Ordered from least to most privileged so that
 * {@link #atLeast(Role)} comparisons are meaningful. {@link #CLIENT} sits at
 * the bottom: it is an external, read-only account with no internal powers.
 */
public enum Role {

    CLIENT,
    ASSOCIATE,
    COORDINATOR,
    MANAGER,
    ADMIN;

    /** {@code true} for MANAGER and ADMIN. */
    public boolean isManagerOrAbove() {
        return this == MANAGER || this == ADMIN;
    }

    /** {@code true} for COORDINATOR, MANAGER and ADMIN. */
    public boolean isCoordinatorOrAbove() {
        return this == COORDINATOR || isManagerOrAbove();
    }

    /** {@code true} for the external client accounts. */
    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** {@code true} when this role is at least as privileged as {@code required}. */
    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
