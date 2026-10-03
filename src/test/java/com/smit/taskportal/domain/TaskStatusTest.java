package com.smit.taskportal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    void openAndInProgressAreActive() {
        assertThat(TaskStatus.OPEN.isActive()).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.isActive()).isTrue();
        assertThat(TaskStatus.QUALITY.isActive()).isTrue();
        assertThat(TaskStatus.SUBMITTED.isActive()).isTrue();
        assertThat(TaskStatus.CLOSED.isActive()).isFalse();
    }

    @Test
    void closedIsTheOnlyFinalState() {
        assertThat(TaskStatus.CLOSED.isFinal()).isTrue();
        assertThat(TaskStatus.OPEN.isFinal()).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.isFinal()).isFalse();
        assertThat(TaskStatus.QUALITY.isFinal()).isFalse();
        assertThat(TaskStatus.SUBMITTED.isFinal()).isFalse();
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
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.QUALITY)).isTrue();
        assertThat(TaskStatus.QUALITY.canTransitionTo(TaskStatus.SUBMITTED)).isTrue();
    }

    @Test
    void reworkAndReopenAreAllowed() {
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.OPEN)).isTrue();
        assertThat(TaskStatus.QUALITY.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.CLOSED.canTransitionTo(TaskStatus.OPEN)).isTrue();
    }

    @Test
    void skippingStepsIsRejected() {
        assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.QUALITY)).isFalse();
        assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.CLOSED)).isTrue();
        assertThat(TaskStatus.QUALITY.canTransitionTo(TaskStatus.OPEN)).isTrue();
    }

    @Test
    void nullTargetIsTolerated() {
        assertThat(TaskStatus.OPEN.canTransitionTo(null)).isTrue();
    }
}
