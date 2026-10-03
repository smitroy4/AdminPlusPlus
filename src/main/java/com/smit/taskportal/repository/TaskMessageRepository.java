package com.smit.taskportal.repository;

import com.smit.taskportal.domain.TaskMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and m.escalation = false
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findByTaskId(@Param("taskId") Long taskId);

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and m.escalation = false and m.internal = false
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findByTaskIdAndInternalFalse(@Param("taskId") Long taskId);

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and m.escalation = false
              and (m.internal = false or m.fromUser.id = :viewerId)
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findVisibleByTaskId(@Param("taskId") Long taskId, @Param("viewerId") Long viewerId);

    /** The escalation conversation of a task (participants only — enforced by the service). */
    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and m.escalation = true
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findEscalationsByTaskId(@Param("taskId") Long taskId);

    Optional<TaskMessage> findByIdAndTaskId(Long id, Long taskId);

    long countByTaskId(Long taskId);

    long countByTaskIdAndEscalationFalse(Long taskId);

    // ------------------------------------------------------------ notifications

    /**
     * Recent escalation messages across every task. Which of them the caller may
     * actually see (their own client escalations, or every one for managers and
     * admins) is decided by {@code NotificationService}.
     */
    @Query("""
            select m from TaskMessage m
            join fetch m.task t
            join fetch m.fromUser u
            where m.escalation = true and m.createdAt >= :since
            order by m.createdAt desc, m.id desc
            """)
    List<TaskMessage> findRecentEscalations(@Param("since") Instant since);

    /** Recent thread messages on tasks the user created or was assigned. */
    @Query("""
            select m from TaskMessage m
            join fetch m.task t
            join fetch m.fromUser u
            where m.escalation = false and m.internal = false
              and m.createdAt >= :since
              and (t.createdBy.id = :userId or t.assignedTo.id = :userId)
              and (t.description is null or m.messageBody <> t.description)
            order by m.createdAt desc, m.id desc
            """)
    List<TaskMessage> findRecentStaffMessages(@Param("userId") Long userId, @Param("since") Instant since);

    /** Recent thread messages on tasks belonging to the caller's customer. */
    @Query("""
            select m from TaskMessage m
            join fetch m.task t
            join fetch m.fromUser u
            where m.escalation = false and m.internal = false
              and m.createdAt >= :since
              and t.client.id = :clientId
              and (t.description is null or m.messageBody <> t.description)
            order by m.createdAt desc, m.id desc
            """)
    List<TaskMessage> findRecentClientMessages(@Param("clientId") Long clientId, @Param("since") Instant since);

    @Modifying
    @Query("delete from TaskMessage m where m.task.id = :taskId")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
