package com.smit.taskportal.domain;

/** Task urgency, ordered from least to most urgent. */
public enum TaskPriority {

    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4);

    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isAtLeast(TaskPriority other) {
        return this.weight >= other.weight;
    }
}
