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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Makes a freshly created database immediately usable: one admin, one manager,
 * two coordinators, two associates, an external client login, three dummy
 * customers and a handful of demo tasks (all raised by the manager).
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

        /* A second coordinator and a second associate, so the operational sheets
           on the dashboard have more than a single row to show. */
        User coordinator2 = ensureUser("coordinator2", "Coordinator2@12345",
                "coordinator2@taskportal.local", Role.COORDINATOR);
        User associate2 = ensureUser("employee2", "Employee2@12345",
                "employee2@taskportal.local", Role.ASSOCIATE);

        Client acme = ensureClient(customer("Acme Corporation",
                "Jane Founder", "jane@acme.example", "+1 555 0100", "ACTIVE",
                "1200 Market Street, Suite 400", "San Francisco", "California", "United States", "94103",
                "Acme Corporation, Inc.", "Manufacturing", "https://acme.example", "500-1000 employees",
                "Enterprise account, priority SLA, quarterly business review."));
        Client globex = ensureClient(customer("Globex Industries",
                "Hank Scorpio", "hank@globex.example", "+1 555 0200", "ACTIVE",
                "88 Renoir Avenue", "Austin", "Texas", "United States", "78701",
                "Globex Industries LLC", "Energy", "https://globex.example", "100-500 employees",
                "Mid-market; bills monthly, prefers email contact."));
        Client initech = ensureClient(customer("Initech Ltd",
                "Peter Gibbons", "peter@initech.example", "+44 20 7946 0000", "ACTIVE",
                "1 Millennium Way", "London", "Greater London", "United Kingdom", "EC2R 8AH",
                "Initech Limited", "Software", "https://initech.example", "50-100 employees",
                "Support retainer, escalation contact is the IT director."));

        applyBilling(acme, "PAID", "ACTIVE", "INVOICE", "MONTHLY", 0d, 184_500d, "INV-2024-0417");
        applyBilling(globex, "PAID", "ACTIVE", "BANK_TRANSFER", "QUARTERLY", 0d, 96_250d, "INV-2024-0398");
        applyBilling(initech, "OVERDUE", "ACTIVE", "CARD", "MONTHLY", 4_200d, 73_900d, "INV-2024-0431");

        User clientUser = ensureUser(seed.getClientUsername(), seed.getClientPassword(), seed.getClientEmail(), Role.CLIENT);
        if (clientUser.getClient() == null || !acme.getId().equals(clientUser.getClient().getId())) {
            clientUser.setClient(acme);
            userRepository.save(clientUser);
        }

        log.info("Seed accounts ready -> admin='{}' manager='{}' coordinator='{}' associate='{}' client='{}'",
                admin.getUsername(), manager.getUsername(), coordinator.getUsername(),
                associate.getUsername(), clientUser.getUsername());

        if (seed.isSampleTasks() && taskRepository.count() == 0) {
            seedTasks(manager, associate, associate2, coordinator, coordinator2,
                    acme, globex, initech);
        }
    }

    private User ensureUser(String username, String password, String email, Role role) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    log.info("Creating seed account '{}' with role {}", username, role);
                    return userService.register(username, password, email, role);
                });
    }

    /* --------------------------------------------------------- demo customers */

    /** Positional sugar for the customer seed block below. */
    private static CustomerSpec customer(String name, String contactName, String email, String phone,
                                         String status, String address, String city, String state,
                                         String country, String postalCode, String organizationName,
                                         String industry, String website, String companySize, String notes) {
        return new CustomerSpec(name, contactName, email, phone, status, address, city, state,
                country, postalCode, organizationName, industry, website, companySize, notes);
    }

    private record CustomerSpec(String name, String contactName, String email, String phone,
                                String status, String address, String city, String state,
                                String country, String postalCode, String organizationName,
                                String industry, String website, String companySize, String notes) {
    }

    private Client ensureClient(CustomerSpec spec) {
        return clientRepository.findByNameIgnoreCase(spec.name())
                .orElseGet(() -> {
                    log.info("Creating seed client '{}'", spec.name());
                    return clientRepository.save(Client.builder()
                            .name(spec.name())
                            .contactName(spec.contactName())
                            .email(spec.email())
                            .phone(spec.phone())
                            .status(spec.status())
                            .address(spec.address())
                            .city(spec.city())
                            .state(spec.state())
                            .country(spec.country())
                            .postalCode(spec.postalCode())
                            .organizationName(spec.organizationName())
                            .industry(spec.industry())
                            .website(spec.website())
                            .companySize(spec.companySize())
                            .notes(spec.notes())
                            .build());
                });
    }

    /**
     * Seeds the commercial billing block: standing, method, cycle, amounts and the
     * last invoice reference. Commercial facts only — the entity models no payment
     * instrument, so there is nothing sensitive to store or leak.
     */
    private void applyBilling(Client client, String paymentStatus, String billingStatus,
                              String paymentMethod, String billingCycle, double outstanding,
                              double totalPaid, String lastReference) {
        if (client.getPaymentMethod() != null) {
            return;
        }
        client.setPaymentStatus(paymentStatus);
        client.setBillingStatus(billingStatus);
        client.setPaymentMethod(paymentMethod);
        client.setBillingCycle(billingCycle);
        client.setOutstandingAmount(BigDecimal.valueOf(outstanding));
        client.setTotalPaid(BigDecimal.valueOf(totalPaid));
        client.setLastPaymentReference(lastReference);
        client.setLastPaymentAt(Instant.now().minus(32, ChronoUnit.DAYS));
        client.setNextPaymentDueAt(Instant.now()
                .plus("QUARTERLY".equals(billingCycle) ? 58 : 12, ChronoUnit.DAYS));
        clientRepository.save(client);
    }

    /* -------------------------------------------------------------- demo work */

    /**
     * Demo tasks are written straight through the repositories: the service
     * layer resolves the caller from the security context, which is anonymous
     * during application startup. Every task is raised by the manager — the only
     * staff role allowed to originate work.
     */
    private void seedTasks(User manager, User associate, User associate2, User coordinator,
                           User coordinator2, Client acme, Client globex, Client initech) {
        createTask("Investigate login latency spike",
                "Support reported intermittent 2-4s logins between 09:00 and 10:00 UTC.\n"
                        + "- Correlate the slow query log with the deploy timeline\n"
                        + "- Check connection pool metrics\n"
                        + "- Report back before end of day",
                TaskPriority.URGENT, TaskStatus.IN_PROGRESS, manager, associate, acme);

        createTask("Draft Q3 capacity plan for the support team",
                "Summarise headcount requests, shift coverage and the training backlog for next quarter.",
                TaskPriority.URGENT, TaskStatus.OPEN, manager, null, globex);

        createTask("Rotate staging database credentials",
                "Rotate the staging DB password and update the deployment secrets. "
                        + "Announce the maintenance window in #ops.",
                TaskPriority.NORMAL, TaskStatus.OPEN, manager, null, null);

        createTask("Archive closed tasks from 2024",
                "Move all 2024 CLOSED tasks into the archive schema and export a CSV for the compliance team.",
                TaskPriority.NORMAL, TaskStatus.CLOSED, manager, associate, acme);

        createTask("Prepare quarterly security review for Acme",
                "Collect the access audit, pen-test findings and remediation status for the Acme QBR deck.",
                TaskPriority.URGENT, TaskStatus.OPEN, manager, coordinator, acme);

        createTask("Onboard Initech SSO integration",
                "Wire Initech up to the SSO broker: metadata exchange, certificate rotation, smoke tests.",
                TaskPriority.NORMAL, TaskStatus.IN_PROGRESS, manager, associate, initech);

        /* A second in-flight item so the sheets can show a mixed Active/Idle board. */
        createTask("Consolidate Globex billing contacts",
                "Replace the shared finance inbox with named billing contacts before the next invoice run.",
                TaskPriority.NORMAL, TaskStatus.IN_PROGRESS, manager, coordinator2, globex);

        createTask("Map Acme support tiers to entitlements",
                "Cross-check the contractual response times against the escalation matrix.",
                TaskPriority.NORMAL, TaskStatus.OPEN, manager, associate2, acme);
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
