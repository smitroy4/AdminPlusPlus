package com.smit.taskportal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPriorityTest {

    @Test
    void weightReflectsUrgency() {
        assertThat(TaskPriority.LOW.getWeight()).isLessThan(TaskPriority.MEDIUM.getWeight());
        assertThat(TaskPriority.MEDIUM.getWeight()).isLessThan(TaskPriority.HIGH.getWeight());
        assertThat(TaskPriority.HIGH.getWeight()).isLessThan(TaskPriority.URGENT.getWeight());
    }

    @Test
    void isAtLeastIsInclusive() {
        assertThat(TaskPriority.HIGH.isAtLeast(TaskPriority.HIGH)).isTrue();
        assertThat(TaskPriority.HIGH.isAtLeast(TaskPriority.MEDIUM)).isTrue();
        assertThat(TaskPriority.MEDIUM.isAtLeast(TaskPriority.URGENT)).isFalse();
    }
}
