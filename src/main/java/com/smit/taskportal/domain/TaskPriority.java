package com.smit.taskportal.domain;

/** Task urgency, ordered from least to most urgent. */
public enum TaskPriority {

    /** The everyday case: pick it up in the normal flow. */
    NORMAL(1),
    /** Drop what you are doing — somebody is waiting on this. */
    URGENT(2);

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
