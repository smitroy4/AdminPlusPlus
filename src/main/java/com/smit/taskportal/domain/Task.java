package com.smit.taskportal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A unit of work. {@code taskNo} is the human readable, monotonically increasing
 * reference (e.g. {@code TASK-00042}) used everywhere in the UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_status", columnList = "status"),
        @Index(name = "idx_tasks_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_tasks_created_by", columnList = "created_by"),
        @Index(name = "idx_tasks_priority", columnList = "priority"),
        @Index(name = "idx_tasks_client", columnList = "client_id")
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "task_no", nullable = false, unique = true, updatable = false, length = 32)
    private String taskNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.NORMAL;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    /** The customer this piece of work is for; may be unset for internal work. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * The client account that escalated this task to a manager; non-null once
     * the escalation conversation exists (see {@code TaskMessage.escalation}).
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_by")
    private User escalatedBy;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<TaskMessage> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * When the current assignee took ownership. It is the event time of the
     * "assigned" notification, so that entry keeps its date while the task
     * moves on (unlike {@link #updatedAt}, which every reply bumps).
     */
    @Column(name = "assigned_at")
    private Instant assignedAt;

    /**
     * When the status last changed. The bell drops everything it reported about
     * this task before this instant — see {@code NotificationService}.
     */
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    /** Adds a comment and keeps both sides of the association in sync. */
    public void addMessage(TaskMessage message) {
        messages.add(message);
        message.setTask(this);
    }

    /**
     * Moves the task along its life-cycle and stamps the moment, which is what
     * makes the outstanding notifications about this task disappear.
     */
    public void changeStatus(TaskStatus target) {
        this.status = target;
        this.statusChangedAt = Instant.now();
    }

    /** True once a client has escalated this task to a manager. */
    public boolean isEscalated() {
        return escalatedBy != null;
    }

    /**
     * Records "something happened here". Thread activity lives in
     * {@code task_messages}, so it would otherwise leave {@link #updatedAt}
     * untouched and the UI would claim a task is stale while people are
     * actively discussing it.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
}
