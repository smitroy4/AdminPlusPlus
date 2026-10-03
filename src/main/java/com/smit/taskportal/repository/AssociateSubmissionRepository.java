package com.smit.taskportal.repository;

import com.smit.taskportal.domain.AssociateSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssociateSubmissionRepository extends JpaRepository<AssociateSubmission, Long> {

    @EntityGraph(attributePaths = {"employee"})
    List<AssociateSubmission> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    @EntityGraph(attributePaths = {"employee"})
    List<AssociateSubmission> findByTaskIdAndStatusOrderByCreatedAtDesc(Long taskId, com.smit.taskportal.domain.AssociateSubmissionStatus status);
}