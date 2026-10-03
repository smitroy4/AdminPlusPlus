package com.smit.taskportal.repository;

import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // ---------------------------------------------------------------- lookups

    /** Eagerly loads assignee/creator/client so list views never trigger N+1 selects. */
    @Override
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    Optional<Task> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findAll();

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByStatus(TaskStatus status);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByStatusIn(Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByStatusInOrderByCreatedAtDesc(Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByAssignedTo(User assignedTo);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByAssignedToAndStatusIn(User assignedTo, Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByCreatedBy(User createdBy);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByCreatedByIdAndStatusIn(Long createdById, Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByAssignedToId(Long assignedToId);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    @Query("select t from Task t where t.createdBy.id = :userId or t.assignedTo.id = :userId")
    List<Task> findByUserId(@Param("userId") Long userId);

    /** Tasks recently touched for a given assignee — feeds the "assigned" notifications. */
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    @Query("""
            select t from Task t
            where t.assignedTo.id = :userId and t.updatedAt >= :since
            order by t.updatedAt desc
            """)
    List<Task> findRecentTasksAssignedTo(@Param("userId") Long userId, @Param("since") Instant since);

    /**
     * Most recently touched tasks in the given statuses for a set of assignees —
     * one query behind the Managers / Coordinators / Associates sheets, so the
     * "current task" of each member is the newest row of their group.
     */
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    @Query("""
            select t from Task t
            where t.assignedTo.id in :assigneeIds and t.status in :statuses
            order by t.updatedAt desc
            """)
    List<Task> findByAssignedToIdInAndStatusInOrderByUpdatedAtDesc(
            @Param("assigneeIds") Collection<Long> assigneeIds,
            @Param("statuses") Collection<TaskStatus> statuses);

    // ---------------------------------------------------------- customer work

    /**
     * Client accounts are never the assignee, so their "my work" counters and
     * tables are keyed on the customer instead.
     */
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByClientId(Long clientId);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    List<Task> findByClientIdAndStatusIn(Long clientId, Collection<TaskStatus> statuses);

    long countByClientId(Long clientId);

    long countByClientIdAndStatusIn(Long clientId, Collection<TaskStatus> statuses);

    // --------------------------------------------------------------- counters

    long countByStatus(TaskStatus status);

    long countByStatusIn(Collection<TaskStatus> statuses);

    long countByAssignedToIdAndStatusIn(Long assignedToId, Collection<TaskStatus> statuses);

    long countByAssignedToId(Long assignedToId);

    @Query("select t.priority, count(t) from Task t group by t.priority")
    List<Object[]> countGroupedByPriority();

    @Query("select t.status, count(t) from Task t group by t.status")
    List<Object[]> countGroupedByStatus();

    /** Breakdown of every task created by or assigned to a user with {@code role}. */
    @Query("select t.priority, count(t) from Task t where t.createdBy.role = :role or t.assignedTo.role = :role group by t.priority")
    List<Object[]> countGroupedByPriorityForRole(@Param("role") Role role);

    @Query("select t.status, count(t) from Task t where t.createdBy.role = :role or t.assignedTo.role = :role group by t.status")
    List<Object[]> countGroupedByStatusForRole(@Param("role") Role role);

    @Query("select count(t) from Task t where t.createdBy.role = :role or t.assignedTo.role = :role")
    long countByRole(@Param("role") Role role);

    @Query("select count(t) from Task t where (t.createdBy.role = :role or t.assignedTo.role = :role) and t.status = :status")
    long countByRoleAndStatus(@Param("role") Role role, @Param("status") TaskStatus status);

    // ---------------------------------------------------------------- manager

    /**
     * Filtered backlog for the manager view. Every criterion is optional and
     * {@code null} means "no restriction".
     */
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "client"})
    @Query("""
            select distinct t from Task t
            where (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assignedToId is null or t.assignedTo.id = :assignedToId)
              and (:unassignedOnly = false or t.assignedTo is null)
              and (:openOnly = false or t.status in :activeStatuses)
            order by t.createdAt desc
            """)
    List<Task> search(@Param("status") TaskStatus status,
                      @Param("priority") TaskPriority priority,
                      @Param("assignedToId") Long assignedToId,
                      @Param("unassignedOnly") boolean unassignedOnly,
                      @Param("openOnly") boolean openOnly,
                      @Param("activeStatuses") Collection<TaskStatus> activeStatuses);
}
