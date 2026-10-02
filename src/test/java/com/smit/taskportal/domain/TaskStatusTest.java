package com.smit.taskportal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    void openAndInProgressAreActive() {
        assertThat(TaskStatus.OPEN.isActive()).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.isActive()).isTrue();
        assertThat(TaskStatus.COMPLETED.isActive()).isFalse();
        assertThat(TaskStatus.CLOSED.isActive()).isFalse();
    }

    @Test
    void completedAndClosedAreFinal() {
        assertThat(TaskStatus.COMPLETED.isFinal()).isTrue();
        assertThat(TaskStatus.CLOSED.isFinal()).isTrue();
        assertThat(TaskStatus.OPEN.isFinal()).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.isFinal()).isFalse();
    }

    @Test
    void statusCanAlwaysBeReapplied() {
        for (TaskStatus status : TaskStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    void happyPathIsAllowed() {
        assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.COMPLETED)).isTrue();
        assertThat(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.CLOSED)).isTrue();
    }

    @Test
    void reworkAndReopenAreAllowed() {
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.OPEN)).isTrue();
        assertThat(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.CLOSED.canTransitionTo(TaskStatus.OPEN)).isTrue();
    }

    @Test
    void skippingStepsIsRejected() {
        assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.COMPLETED)).isFalse();
        assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.CLOSED)).isTrue();
        assertThat(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.OPEN)).isTrue();
    }

    @Test
    void nullTargetIsTolerated() {
        assertThat(TaskStatus.OPEN.canTransitionTo(null)).isTrue();
    }
}
