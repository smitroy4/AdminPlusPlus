package com.smit.taskportal.repository;

import com.smit.taskportal.domain.TaskMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("select m from TaskMessage m where m.task.id = :taskId order by m.createdAt asc, m.id asc")
    List<TaskMessage> findByTaskId(@Param("taskId") Long taskId);

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and m.internal = false
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findByTaskIdAndInternalFalse(@Param("taskId") Long taskId);

    @EntityGraph(attributePaths = {"fromUser"})
    @Query("""
            select m from TaskMessage m
            where m.task.id = :taskId and (m.internal = false or m.fromUser.id = :viewerId)
            order by m.createdAt asc, m.id asc
            """)
    List<TaskMessage> findVisibleByTaskId(@Param("taskId") Long taskId, @Param("viewerId") Long viewerId);

    Optional<TaskMessage> findByIdAndTaskId(Long id, Long taskId);

    long countByTaskId(Long taskId);

    @Modifying
    @Query("delete from TaskMessage m where m.task.id = :taskId")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
