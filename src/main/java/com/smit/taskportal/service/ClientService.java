package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.ClientDto;
import com.smit.taskportal.api.dto.ClientProfileDto;
import com.smit.taskportal.api.dto.TaskDto;
import com.smit.taskportal.domain.Client;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.ClientRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Client catalogue reads. What a caller may see of a customer depends on who
 * they are:
 *
 * <ul>
 *   <li>MANAGER / ADMIN — the full record (see {@link ClientDto#detailed}).</li>
 *   <li>a CLIENT account — the full commercial record of <em>its own</em>
 *       customer, minus the internal notes; any other id answers 404, so the
 *       platform never confirms that another customer's record exists.</li>
 *   <li>ASSOCIATE / COORDINATOR — name, status and creation date only, because
 *       that is all the task tables need.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ClientService {

    /** How many tasks the Client Details page lists under "Recent tasks". */
    private static final int RECENT_TASK_LIMIT = 10;

    private final ClientRepository clientRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final CurrentUserHolder currentUser;

    public ClientService(ClientRepository clientRepository,
                         TaskRepository taskRepository,
                         TaskService taskService,
                         CurrentUserHolder currentUser) {
        this.clientRepository = clientRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    public List<ClientDto> list() {
        AppUserPrincipal actor = currentUser.require();
        if (actor.isClient()) {
            return ownCustomer(actor).stream()
                    .map(client -> ClientDto.ownCustomer(client))
                    .toList();
        }
        return clientRepository.findAllByOrderByNameAsc().stream()
                .map(client -> project(client, actor))
                .toList();
    }

    public ClientDto get(Long id) {
        AppUserPrincipal actor = currentUser.require();
        return project(requireVisible(id, actor), actor);
    }

    /**
     * The Client Details page payload: the customer record plus the task numbers
     * behind it.
     *
     * <p>Counters and the recent-task list are built from the customer's tasks
     * filtered through {@link TaskService#canView}, so they never reveal more
     * than the caller could already open themselves.
     */
    public ClientProfileDto getProfile(Long id) {
        AppUserPrincipal actor = currentUser.require();
        Client client = requireVisible(id, actor);

        List<Task> visible = taskRepository.findByClientId(id).stream()
                .filter(task -> TaskService.canView(actor, task))
                .sorted(Comparator.comparing(Task::getUpdatedAt).reversed())
                .toList();

        ClientProfileDto.TaskStats stats = new ClientProfileDto.TaskStats(
                visible.size(),
                count(visible, TaskStatus.OPEN),
                count(visible, TaskStatus.IN_PROGRESS),
                count(visible, TaskStatus.QUALITY),
                count(visible, TaskStatus.SUBMITTED),
                count(visible, TaskStatus.CLOSED));

        List<TaskDto> recent = visible.stream()
                .limit(RECENT_TASK_LIMIT)
                .map(TaskDto::from)
                .toList();

        return new ClientProfileDto(project(client, actor), stats, recent);
    }

    public Client getEntity(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    /** Loads the customer, refusing a CLIENT account that is asking for somebody else's. */
    private Client requireVisible(Long id, AppUserPrincipal actor) {
        Client client = getEntity(id);
        if (actor.isClient() && !Objects.equals(client.getId(), actor.clientId())) {
            throw ResourceNotFoundException.of("Client", id);
        }
        return client;
    }

    /** Picks the projection the caller is entitled to. */
    private static ClientDto project(Client client, AppUserPrincipal actor) {
        if (actor.isClient()) {
            return ClientDto.ownCustomer(client);
        }
        return actor.isManagerOrAbove() ? ClientDto.detailed(client) : ClientDto.summary(client);
    }

    /** The single customer a client account is linked to, if any. */
    private List<Client> ownCustomer(AppUserPrincipal actor) {
        if (actor.clientId() == null) {
            return List.of();
        }
        return clientRepository.findById(actor.clientId())
                .map(client -> List.of(client))
                .orElseGet(List::of);
    }

    private static long count(List<Task> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }
}
