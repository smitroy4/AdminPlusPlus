package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.AssociateSubmissionDto;
import com.smit.taskportal.api.dto.TaskMessageDto;
import com.smit.taskportal.domain.AssociateSubmission;
import com.smit.taskportal.domain.AssociateSubmissionStatus;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskMessage;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.exception.ForbiddenException;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.AssociateSubmissionRepository;
import com.smit.taskportal.repository.TaskMessageRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssociateSubmissionService {

    private final AssociateSubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final TaskMessageRepository taskMessageRepository;
    private final CurrentUserHolder currentUser;

    public AssociateSubmissionService(AssociateSubmissionRepository submissionRepository,
                                     TaskRepository taskRepository,
                                     TaskService taskService,
                                     UserRepository userRepository,
                                     TaskMessageRepository taskMessageRepository,
                                     CurrentUserHolder currentUser) {
        this.submissionRepository = submissionRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.userRepository = userRepository;
        this.taskMessageRepository = taskMessageRepository;
        this.currentUser = currentUser;
    }

    /**
     * The accordions in the "Submitted for Quality Approval" section below the
     * composer.
     *
     * <p>Every internal role reads the same list (newest first, never pruned),
     * so the workflow looks identical wherever it is opened. Client accounts are
     * external and never see internal review traffic, so they get an empty list.
     */
    public List<AssociateSubmissionDto> getSubmissions(Long taskId) {
        AppUserPrincipal actor = currentUser.require();
        taskService.getVisibleTask(taskId);

        if (actor.isClient()) {
            return List.of();
        }

        return submissionRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(AssociateSubmissionDto::from)
                .toList();
    }

    /**
     * Captures an associate's update as a submission for the reviewers instead of
     * a conversation message, and parks the task in {@link TaskStatus#QUALITY}.
     */
    @Transactional
    public AssociateSubmissionDto createSubmission(Long taskId, String content) {
        AppUserPrincipal actor = currentUser.require();
        if (!actor.isAssociate()) {
            throw new ForbiddenException("Only associates submit updates for review");
        }

        Task task = taskService.getVisibleTask(taskId);
        User employee = userRepository.findById(actor.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.id()));

        AssociateSubmission submission = AssociateSubmission.builder()
                .task(task)
                .employee(employee)
                .content(content.strip())
                .status(AssociateSubmissionStatus.PENDING)
                .build();

        submission = submissionRepository.save(submission);

        // Workflow step 1: the task now waits for a Quality review.
        if (task.getStatus() != TaskStatus.CLOSED && task.getStatus() != TaskStatus.QUALITY) {
            task.changeStatus(TaskStatus.QUALITY);
        }
        task.touch();
        taskRepository.save(task);

        return AssociateSubmissionDto.from(submission);
    }

    /**
     * Workflow step 2: a coordinator/manager/admin answers the submission. The
     * answer lands in the shared conversation and the task moves on to
     * {@link TaskStatus#SUBMITTED}.
     */
    @Transactional
    public TaskMessageDto reviewSubmission(Long taskId, Long submissionId, String response) {
        AppUserPrincipal actor = currentUser.require();
        if (!actor.isCoordinatorOrAbove()) {
            throw new ForbiddenException("Only Coordinator, Manager, or Admin can review submissions");
        }

        Task task = taskService.getVisibleTask(taskId);
        AssociateSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Submission", submissionId));
        if (!submission.getTask().getId().equals(taskId)) {
            throw new ForbiddenException("Submission does not belong to this task");
        }

        User reviewer = userRepository.findById(actor.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.id()));

        // Persist the reply first: saving the task afterwards would cascade over an
        // unmanaged copy of it and leave the thread with the answer twice.
        TaskMessage responseMessage = taskMessageRepository.save(TaskMessage.builder()
                .task(task)
                .messageBody(response)
                .internal(false)
                .fromUser(reviewer)
                .build());
        task.addMessage(responseMessage);
        task.touch();

        if (task.getStatus() != TaskStatus.CLOSED) {
            task.changeStatus(TaskStatus.SUBMITTED);
        }
        taskRepository.save(task);

        // Mark submission as reviewed
        submission.setStatus(AssociateSubmissionStatus.REVIEWED);
        submission.setReviewedBy(reviewer);
        submission.setReviewedAt(Instant.now());
        submissionRepository.save(submission);

        return TaskMessageDto.from(responseMessage);
    }
}