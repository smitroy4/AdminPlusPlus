package com.smit.taskportal.bootstrap;

import com.smit.taskportal.config.AppProperties;
import com.smit.taskportal.domain.Client;
import com.smit.taskportal.domain.Role;
import com.smit.taskportal.domain.Task;
import com.smit.taskportal.domain.TaskPriority;
import com.smit.taskportal.domain.TaskStatus;
import com.smit.taskportal.domain.User;
import com.smit.taskportal.repository.ClientRepository;
import com.smit.taskportal.repository.TaskRepository;
import com.smit.taskportal.repository.UserRepository;
import com.smit.taskportal.service.TaskNoGenerator;
import com.smit.taskportal.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a freshly created database immediately usable: one admin, one manager,
 * one coordinator and one associate account, an external client login, three
 * dummy customers and a handful of demo tasks (all raised by the manager).
 *
 * <p>Disable with {@code app.seed.enabled=false}.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ClientRepository clientRepository;
    private final UserService userService;
    private final TaskNoGenerator taskNoGenerator;

    public DataInitializer(AppProperties properties,
                           UserRepository userRepository,
                           TaskRepository taskRepository,
                           ClientRepository clientRepository,
                           UserService userService,
                           TaskNoGenerator taskNoGenerator) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.clientRepository = clientRepository;
        this.userService = userService;
        this.taskNoGenerator = taskNoGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = properties.getSeed();
        if (!seed.isEnabled()) {
            log.info("Seed data disabled (app.seed.enabled=false)");
            return;
        }

        User admin = ensureUser(seed.getAdminUsername(), seed.getAdminPassword(), seed.getAdminEmail(), Role.ADMIN);
        User manager = ensureUser(seed.getManagerUsername(), seed.getManagerPassword(), seed.getManagerEmail(), Role.MANAGER);
        User coordinator = ensureUser(seed.getCoordinatorUsername(), seed.getCoordinatorPassword(), seed.getCoordinatorEmail(), Role.COORDINATOR);
        User associate = ensureUser(seed.getEmployeeUsername(), seed.getEmployeePassword(), seed.getEmployeeEmail(), Role.ASSOCIATE);

        Client acme = ensureClient("Acme Corporation", "Jane Founder", "jane@acme.example",
                "+1 555 0100", "Enterprise account, priority SLA, quarterly business review.");
        Client globex = ensureClient("Globex Industries", "Hank Scorpio", "hank@globex.example",
                "+1 555 0200", "Mid-market; bills monthly, prefers email contact.");
        Client initech = ensureClient("Initech Ltd", "Peter Gibbons", "peter@initech.example",
                "+44 20 7946 0000", "Support retainer, escalation contact is the IT director.");

        User clientUser = ensureUser(seed.getClientUsername(), seed.getClientPassword(), seed.getClientEmail(), Role.CLIENT);
        if (clientUser.getClient() == null || !acme.getId().equals(clientUser.getClient().getId())) {
            clientUser.setClient(acme);
            userRepository.save(clientUser);
        }

        log.info("Seed accounts ready -> admin='{}' manager='{}' coordinator='{}' associate='{}' client='{}'",
                admin.getUsername(), manager.getUsername(), coordinator.getUsername(),
                associate.getUsername(), clientUser.getUsername());

        if (seed.isSampleTasks() && taskRepository.count() == 0) {
            seedTasks(manager, associate, coordinator, acme, globex, initech);
        }
    }

    private User ensureUser(String username, String password, String email, Role role) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    log.info("Creating seed account '{}' with role {}", username, role);
                    return userService.register(username, password, email, role);
                });
    }

    private Client ensureClient(String name, String contactName, String email, String phone, String notes) {
        return clientRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    log.info("Creating seed client '{}'", name);
                    return clientRepository.save(Client.builder()
                            .name(name)
                            .contactName(contactName)
                            .email(email)
                            .phone(phone)
                            .notes(notes)
                            .build());
                });
    }

    /**
     * Demo tasks are written straight through the repositories: the service
     * layer resolves the caller from the security context, which is anonymous
     * during application startup. Every task is raised by the manager —
     * associates and coordinators may not create tasks.
     */
    private void seedTasks(User manager, User associate, User coordinator,
                           Client acme, Client globex, Client initech) {
        createTask("Investigate login latency spike",
                "Support reported intermittent 2-4s logins between 09:00 and 10:00 UTC.\n"
                        + "- Correlate the slow query log with the deploy timeline\n"
                        + "- Check connection pool metrics\n"
                        + "- Report back before end of day",
                TaskPriority.URGENT, TaskStatus.IN_PROGRESS, manager, associate, acme);

        createTask("Draft Q3 capacity plan for the support team",
                "Summarise headcount requests, shift coverage and the training backlog for next quarter.",
                TaskPriority.HIGH, TaskStatus.OPEN, manager, null, globex);

        createTask("Rotate staging database credentials",
                "Rotate the staging DB password and update the deployment secrets. "
                        + "Announce the maintenance window in #ops.",
                TaskPriority.MEDIUM, TaskStatus.OPEN, manager, null, null);

        createTask("Archive closed tasks from 2024",
                "Move all 2024 CLOSED tasks into the archive schema and export a CSV for the compliance team.",
                TaskPriority.LOW, TaskStatus.CLOSED, manager, associate, acme);

        createTask("Prepare quarterly security review for Acme",
                "Collect the access audit, pen-test findings and remediation status for the Acme QBR deck.",
                TaskPriority.HIGH, TaskStatus.OPEN, manager, coordinator, acme);

        createTask("Onboard Initech SSO integration",
                "Wire Initech up to the SSO broker: metadata exchange, certificate rotation, smoke tests.",
                TaskPriority.MEDIUM, TaskStatus.IN_PROGRESS, manager, associate, initech);
    }

    private void createTask(String title, String description, TaskPriority priority, TaskStatus status,
                            User creator, User assignee, Client client) {
        taskRepository.save(Task.builder()
                .taskNo(taskNoGenerator.next())
                .title(title)
                .description(description)
                .priority(priority)
                .status(status)
                .createdBy(creator)
                .assignedTo(assignee)
                .client(client)
                .build());
    }
}
