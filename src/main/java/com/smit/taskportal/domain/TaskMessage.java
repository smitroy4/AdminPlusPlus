package com.smit.taskportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A comment / reply on a task. The first message of a thread is the task
 * description itself, which is stored as a regular (non internal) message.
 *
 * <p>Internal notes are only ever exposed to managers and admins (plus their
 * own author) — see {@code TaskMessageService}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_messages", indexes = {
        @Index(name = "idx_task_messages_task", columnList = "task_id"),
        @Index(name = "idx_task_messages_from_user", columnList = "from_user_id")
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TaskMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @Column(name = "message_body", nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Builder.Default
    @Column(name = "is_internal", nullable = false)
    private boolean internal = false;

    /**
     * Part of the "escalate to manager" conversation raised by a client.
     * Escalation messages never appear in the regular thread — they are only
     * exposed to managers/admins and the client who escalated the task.
     */
    @Builder.Default
    @Column(name = "is_escalation", nullable = false)
    private boolean escalation = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
