package com.smit.taskportal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPriorityTest {

    @Test
    void weightReflectsUrgency() {
        assertThat(TaskPriority.NORMAL.getWeight()).isLessThan(TaskPriority.URGENT.getWeight());
        assertThat(TaskPriority.values()).hasSize(2);
    }

    @Test
    void isAtLeastIsInclusive() {
        assertThat(TaskPriority.URGENT.isAtLeast(TaskPriority.URGENT)).isTrue();
        assertThat(TaskPriority.NORMAL.isAtLeast(TaskPriority.NORMAL)).isTrue();
        assertThat(TaskPriority.NORMAL.isAtLeast(TaskPriority.URGENT)).isFalse();
    }
}
