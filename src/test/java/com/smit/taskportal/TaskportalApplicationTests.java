package com.smit.taskportal;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end smoke test over the configured database. The schema is created by
 * {@code ddl-auto=create} and the accounts come from the {@code app.seed}
 * block, so the test is self-contained.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskportalApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedApiCallIsRejectedWithJson401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void csrfEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void managerCanSignInAndReadTheDashboard() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("manager"))
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(get("/api/dashboard/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canViewTeam").value(true))
                .andExpect(jsonPath("$.data.canViewAll").value(true));

        mockMvc.perform(get("/api/tasks/all-open").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void associateCannotReachManagerEndpointsButCanReadAllOpenTasks() throws Exception {
        MockHttpSession session = login("employee", "Employee@12345");

        mockMvc.perform(get("/api/tasks/all-open").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/tasks/all").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/manager/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotCreateATask() throws Exception {
        MockHttpSession session = login("employee", "Employee@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Associates must not raise tasks","priority":"NORMAL"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void coordinatorCannotCreateATaskOrReachManagerEndpoints() throws Exception {
        MockHttpSession session = login("coordinator", "Coordinator@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Coordinators must not raise tasks","priority":"NORMAL"}"""))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tasks/all-open").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/manager/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorSeesAssociateMetricsButNotCompanyWide() throws Exception {
        MockHttpSession session = login("coordinator", "Coordinator@12345");

        mockMvc.perform(get("/api/dashboard/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canViewTeam").value(true))
                .andExpect(jsonPath("$.data.canViewAll").value(false));
    }

    @Test
    void associateSeesOnlyOwnMetrics() throws Exception {
        MockHttpSession session = login("employee", "Employee@12345");

        mockMvc.perform(get("/api/dashboard/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canViewTeam").value(false))
                .andExpect(jsonPath("$.data.canViewAll").value(false));
    }

    @Test
    void clientDashboardMetricsCountTheirOwnCustomersTasks() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");

        long acmeId = -1;
        long globexId = -1;
        for (JsonNode one : dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn())) {
            if ("Acme Corporation".equals(one.get("name").asText())) {
                acmeId = one.get("id").asLong();
            }
            if ("Globex Industries".equals(one.get("name").asText())) {
                globexId = one.get("id").asLong();
            }
        }
        assertThat(acmeId).as("seeded Acme client").isPositive();
        assertThat(globexId).as("seeded Globex client").isPositive();

        JsonNode before = dashboardStats(client);
        assertThat(before.get("canViewTeam").asBoolean()).isFalse();
        assertThat(before.get("canViewAll").asBoolean()).isFalse();

        long acmeTask = createTask(manager, """
                {"title":"Client metrics probe - Acme","priority":"NORMAL","clientId":%d}"""
                .formatted(acmeId));
        long globexTask = createTask(manager, """
                {"title":"Client metrics probe - Globex","priority":"NORMAL","clientId":%d}"""
                .formatted(globexId));

        JsonNode after = dashboardStats(client);
        assertThat(after.get("myTotal").asLong())
                .as("only tasks of the caller's own customer are 'my work'")
                .isEqualTo(before.get("myTotal").asLong() + 1);
        assertThat(after.get("myOpen").asLong())
                .isEqualTo(before.get("myOpen").asLong() + 1);

        // The charts are scoped exactly like the tiles.
        assertThat(bucketCount(after, "byStatus", "OPEN"))
                .as("the OPEN bucket mirrors the Open tile")
                .isEqualTo(after.get("myOpen").asLong());

        // ...and so is the "My open tasks" table underneath them.
        JsonNode myOpen = dataOf(mockMvc.perform(get("/api/tasks/my-open").session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(containsId(myOpen, acmeTask))
                .as("the customer's own new task is listed")
                .isTrue();
        for (JsonNode task : myOpen) {
            assertThat(task.get("client").get("name").asText()).isEqualTo("Acme Corporation");
            assertThat(task.get("id").asLong())
                    .as("another customer's task never leaks in")
                    .isNotEqualTo(globexTask);
            assertThat(task.get("status").asText())
                    .as("finished work is not 'open'")
                    .isNotEqualTo("CLOSED");
        }
    }

    private JsonNode dashboardStats(MockHttpSession session) throws Exception {
        return dataOf(mockMvc.perform(get("/api/dashboard/stats").session(session))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static long bucketCount(JsonNode stats, String field, String label) {
        for (JsonNode bucket : stats.get(field)) {
            if (label.equals(bucket.get("label").asText())) {
                return bucket.get("count").asLong();
            }
        }
        return -1;
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    @Test
    void userCanUpdateOwnProfileAndAdminCanEditAnyAccount() throws Exception {
        MockHttpSession employee = login("employee", "Employee@12345");
        MockHttpSession admin = login("admin", "Admin@12345");

        // Self-service: contact details only.
        mockMvc.perform(put("/api/me/profile")
                                .session(employee)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"associate-updated@taskportal.local"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("associate-updated@taskportal.local"));

        // Admin: any information on any account, plus a password reset.
        MvcResult me = mockMvc.perform(get("/api/me").session(employee))
                .andExpect(status().isOk())
                .andReturn();
        long employeeId = idOf(me.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/admin/users/" + employeeId)
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"associate-renamed","email":"associate-renamed@taskportal.local"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("associate-renamed"))
                .andExpect(jsonPath("$.data.email").value("associate-renamed@taskportal.local"));

        mockMvc.perform(patch("/api/admin/users/" + employeeId + "/password")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Reset@123456"}"""))
                .andExpect(status().isOk());

        login("associate-renamed", "Reset@123456");

        // Restore the seed account so repeated runs stay stable.
        mockMvc.perform(patch("/api/admin/users/" + employeeId)
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"employee","email":"employee@taskportal.local"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + employeeId + "/password")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Employee@12345"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminsCannotTouchUserManagementEndpoints() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        mockMvc.perform(patch("/api/admin/users/1")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nope@taskportal.local"}"""))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/1/password")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Nope@123456"}"""))
                .andExpect(status().isForbidden());
    }

    /**
     * The associate message workflow: an associate never writes into the
     * conversation. The same words are filed as a submission for the reviewers,
     * the task parks itself in QUALITY, and every internal role reads the same
     * accordion list without anything ever being overwritten.
     */
    @Test
    void associateUpdateBecomesASubmissionInsteadOfAConversationMessage() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");
        MockHttpSession client = login("client", "Client@12345");

        long taskId = createTaskForEmployee(manager, employee);

        // Straight into the thread: refused.
        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"First reply from the test suite","internal":false}"""))
                .andExpect(status().isForbidden());

        // The same words as a submission: accepted, with the author attached.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"First reply from the test suite\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.employee.username").value("employee"));

        // The thread keeps only the seeded description, and the task waits for review.
        mockMvc.perform(get("/api/task/" + taskId).session(coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("QUALITY"))
                .andExpect(jsonPath("$.data.messages.length()").value(1));

        // Only associates file submissions.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not how reviewers contribute\"}"))
                .andExpect(status().isForbidden());

        // A second update stacks on top of the first rather than replacing it.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Second update from the associate\"}"))
                .andExpect(status().isCreated());

        for (String[] account : new String[][]{
                {"coordinator", "Coordinator@12345"},
                {"employee", "Employee@12345"}}) {
            MockHttpSession session = login(account[0], account[1]);
            JsonNode submissions = dataOf(mockMvc.perform(
                            get("/api/task/" + taskId + "/associate-submissions").session(session))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(submissions.size())
                    .as("%s sees both submissions", account[0])
                    .isEqualTo(2);
            assertThat(List.of(submissions.get(0).get("content").asText(),
                            submissions.get(1).get("content").asText()))
                    .containsExactlyInAnyOrder("First reply from the test suite",
                            "Second update from the associate");
        }

        // A client cannot even open this task, let alone its review traffic.
        mockMvc.perform(get("/api/task/" + taskId + "/associate-submissions").session(client))
                .andExpect(status().isForbidden());

        // On a task of its own customer the endpoint answers — with nothing to show.
        long acmeId = idByName(dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn()), "Acme Corporation");
        long acmeTask = createTask(manager, """
                {"title":"Acme submission privacy probe","priority":"NORMAL","clientId":%d}"""
                .formatted(acmeId));
        mockMvc.perform(post("/api/task/" + acmeTask + "/associate-submission")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Only the internal team reads this\"}"))
                .andExpect(status().isCreated());

        JsonNode forClient = dataOf(mockMvc.perform(
                        get("/api/task/" + acmeTask + "/associate-submissions").session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(forClient.size()).isZero();
    }

    /**
     * The backend drives the life-cycle: an associate's update parks the task in
     * QUALITY, and whatever a coordinator/manager/admin sends next moves it on
     * to SUBMITTED — both without anybody touching the status selector.
     */
    @Test
    void submissionReviewDrivesQualityAndSubmittedStatus() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        long taskId = createTaskForEmployee(manager, employee);

        long submissionId = idOf(mockMvc.perform(post("/api/task/" + taskId + "/associate-submission")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Build is ready for review\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(statusOf(manager, taskId)).isEqualTo("QUALITY");

        // Associates may not review their own (or anybody else's) submission.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission/" + submissionId + "/review")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"self review\"}"))
                .andExpect(status().isForbidden());

        // The coordinator answers: the reply lands in the thread, the task moves on.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission/" + submissionId + "/review")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Reviewed - looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageBody").value("Reviewed - looks good"));
        assertThat(statusOf(manager, taskId)).isEqualTo("SUBMITTED");
        JsonNode afterReview = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(manager))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(afterReview.get("messages").size())
                .as("seeded description + the reviewer's reply: %s", afterReview.get("messages"))
                .isEqualTo(2);

        // The same holds for a plain composer reply while the task waits in Quality.
        mockMvc.perform(post("/api/task/" + taskId + "/associate-submission")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Second round of changes\"}"))
                .andExpect(status().isCreated());
        assertThat(statusOf(manager, taskId)).isEqualTo("QUALITY");

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Back to you for the final check\"}"))
                .andExpect(status().isCreated());
        assertThat(statusOf(manager, taskId)).isEqualTo("SUBMITTED");
    }

    /** Deleting a conversation message is an admin power and nothing else. */
    @Test
    void onlyAdminsCanDeleteMessages() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");
        MockHttpSession employee = login("employee", "Employee@12345");
        MockHttpSession client = login("client", "Client@12345");
        MockHttpSession admin = login("admin", "Admin@12345");

        long acmeTask = taskIdByTitle(manager, "Prepare quarterly security review for Acme");
        long messageId = messageIdOf(manager, acmeTask);

        for (MockHttpSession session : List.of(manager, coordinator, employee, client)) {
            mockMvc.perform(delete("/api/task/" + acmeTask + "/message/" + messageId)
                            .session(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(delete("/api/task/" + acmeTask + "/message/" + messageId)
                        .session(admin)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * Every internal role may open every task, even one it neither created nor
     * is assigned to. Client accounts stay scoped to their own customer.
     */
    @Test
    void everyInternalRoleCanOpenAnyTaskDetail() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");
        MockHttpSession client = login("client", "Client@12345");

        long taskId = createTask(manager, """
                {"title":"Task nobody on the team owns yet",
                 "description":"Raised by the manager for the whole team",
                 "priority":"NORMAL"}""");

        mockMvc.perform(get("/api/task/" + taskId).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.title").value("Task nobody on the team owns yet"));

        mockMvc.perform(get("/api/task/" + taskId).session(coordinator))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/task/" + taskId).session(manager))
                .andExpect(status().isOk());

        // A task with no client at all is out of reach for a client account.
        mockMvc.perform(get("/api/task/" + taskId).session(client))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationFailuresReturn400WithAMessage() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"no title","priority":"NORMAL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingTaskReturns404() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(get("/api/task/999999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void employeesCannotPostInternalNotes() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");

        long taskId = createTaskForEmployee(manager, employee);

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"should be rejected","internal":true}"""))
                .andExpect(status().isForbidden());
    }

    /**
     * Regression test for the weakness of a bare cookie based CSRF repository:
     * the browser attaches {@code XSRF-TOKEN} to cross-site requests too, so a
     * cookie on its own must never be accepted as proof of intent.
     */
    @Test
    void cookieAloneIsNotEnoughForMutatingRequests() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", "value-a-third-party-page-cannot-read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Cookie only must be rejected","priority":"NORMAL"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** No cookie, no header, no form field: nothing is delivered, so it is refused. */
    @Test
    void requestWithoutAnyDeliveredTokenIsRejected() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nothing delivered must be rejected","priority":"NORMAL"}"""))
                .andExpect(status().isForbidden());
    }

    /** {@code internal} must stay optional: omitting it must not fail deserialisation. */
    @Test
    void messageWithoutTheInternalFlagIsAccepted() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        long taskId = createTask(manager, """
                {"title":"Optional internal flag probe","priority":"NORMAL"}""");

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"No internal field at all"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.internal").value(false));
    }

    /**
     * Guards the write paths of {@code UserService}. They sit under a class level
     * {@code @Transactional(readOnly = true)}, so every mutating method must opt
     * back in to a read-write transaction or Hibernate rejects the INSERT/UPDATE.
     */
    @Test
    void adminCanCreateAUserChangeItsRoleAndTheUserCanChangeItsPassword() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345");
        String username = "smoke-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        MvcResult created = mockMvc.perform(post("/api/admin/users")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@taskportal.local",
                                 "password":"Smoke@12345","role":"ASSOCIATE"}"""
                                .formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();

        long newUserId = created.getResponse().getContentAsString().contains("\"id\":")
                ? idOf(created.getResponse().getContentAsString())
                : -1;

        mockMvc.perform(patch("/api/admin/users/" + newUserId + "/role")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MANAGER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MANAGER"));

        // The new account really works, including its own password change.
        MockHttpSession fresh = login(username, "Smoke@12345");
        mockMvc.perform(get("/api/me").session(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/auth/change-password")
                        .session(fresh)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Wrong@12345","newPassword":"Smoke@123456"}"""))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(fresh)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Smoke@12345","newPassword":"Smoke@123456"}"""))
                .andExpect(status().isOk());

        login(username, "Smoke@123456");
    }

    @Test
    void theLastAdminCannotBeDemoted() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345");

        mockMvc.perform(patch("/api/admin/users/1/role")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MANAGER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void duplicateUsernamesAreRejected() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345");

        mockMvc.perform(post("/api/admin/users")
                        .session(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"manager","email":"dupe@taskportal.local",
                                 "password":"Smoke@12345","role":"ASSOCIATE"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void everyRoleCanReadTheOpenBacklogAndClosedOnlyThroughAllTasks() throws Exception {
        for (String[] account : new String[][]{
                {"employee", "Employee@12345"},
                {"coordinator", "Coordinator@12345"},
                {"client", "Client@12345"}}) {
            MockHttpSession session = login(account[0], account[1]);

            mockMvc.perform(get("/api/tasks/all-open").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.status=='CLOSED')]").doesNotExist());

            mockMvc.perform(get("/api/tasks/all").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.status=='CLOSED')]").exists());
        }
    }

    @Test
    void clientLoginOnlySeesTasksOfTheirOwnCustomer() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");

        mockMvc.perform(get("/api/me").session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CLIENT"));

        long acmeTask = taskIdByTitle(manager, "Prepare quarterly security review for Acme");
        long globexTask = taskIdByTitle(manager, "Draft Q3 capacity plan for the support team");

        JsonNode visible = dataOf(mockMvc.perform(get("/api/tasks/all").session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(visible.size()).as("seeded Acme tasks the client may list").isGreaterThan(0);
        for (JsonNode task : visible) {
            assertThat(task.get("client").get("name").asText()).isEqualTo("Acme Corporation");
        }

        mockMvc.perform(get("/api/task/" + acmeTask).session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.client.name").value("Acme Corporation"));

        mockMvc.perform(get("/api/task/" + globexTask).session(client))
                .andExpect(status().isForbidden());

        // The client shares the composer with everybody else: a plain comment
        // lands in the shared thread, un-escalated.
        mockMvc.perform(post("/api/task/" + acmeTask + "/message")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"Please confirm the audit window","internal":false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.internal").value(false))
                .andExpect(jsonPath("$.data.escalation").value(false));

        // ...but never on a task belonging to another customer.
        mockMvc.perform(post("/api/task/" + globexTask + "/message")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Not my customer\",\"internal\":false}"))
                .andExpect(status().isForbidden());

        // Internal notes remain reserved for managers and admins.
        mockMvc.perform(post("/api/task/" + acmeTask + "/message")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Sneaky internal note\",\"internal\":true}"))
                .andExpect(status().isForbidden());

        // Still no reassigning.
        mockMvc.perform(post("/api/task/" + acmeTask + "/assign")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCatalogueAndBacklogOnlyExposeTheirOwnCustomer() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");

        JsonNode staff = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(staff.size()).as("staff still see every customer").isGreaterThan(1);
        long globexId = idByName(staff, "Globex Industries");
        assertThat(globexId).as("seeded Globex client").isPositive();

        // The catalogue collapses to the caller's own customer.
        JsonNode own = dataOf(mockMvc.perform(get("/api/clients").session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(own.size()).isEqualTo(1);
        assertThat(own.get(0).get("name").asText()).isEqualTo("Acme Corporation");
        long acmeId = own.get(0).get("id").asLong();

        mockMvc.perform(get("/api/clients/" + acmeId).session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Corporation"));

        // Another customer's record answers exactly like an unknown id.
        mockMvc.perform(get("/api/clients/" + globexId).session(client))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clients/999999").session(client))
                .andExpect(status().isNotFound());

        // Every backlog view is keyed the same way.
        for (String uri : List.of("/api/tasks/all", "/api/tasks/all-open")) {
            JsonNode backlog = dataOf(mockMvc.perform(get(uri).session(client))
                    .andExpect(status().isOk())
                    .andReturn());
            for (JsonNode task : backlog) {
                assertThat(task.get("client").get("name").asText())
                        .as("%s must not leak another customer", uri)
                        .isEqualTo("Acme Corporation");
            }
        }
    }

    /** Thread activity lives in task_messages, so Task.touch() must move the clock. */
    @Test
    void postingAMessageMarksTheTaskAsUpdated() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        long taskId = taskIdByTitle(manager, "Rotate staging database credentials");

        String before = updatedAtOf(manager, taskId);
        Thread.sleep(1100);

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"bumping the clock\"}"))
                .andExpect(status().isCreated());

        assertThat(updatedAtOf(manager, taskId))
                .as("a comment alone must advance 'Last updated'")
                .isNotEqualTo(before);
    }

    private String updatedAtOf(MockHttpSession session, long taskId) throws Exception {
        return dataOf(mockMvc.perform(get("/api/task/" + taskId).session(session))
                .andExpect(status().isOk())
                .andReturn())
                .get("task").get("updatedAt").asText();
    }

    /** The task's status as the workflow left it. */
    private String statusOf(MockHttpSession session, long taskId) throws Exception {
        return dataOf(mockMvc.perform(get("/api/task/" + taskId).session(session))
                .andExpect(status().isOk())
                .andReturn())
                .get("task").get("status").asText();
    }

    /** The id of the oldest message in the thread (the seeded description). */
    private long messageIdOf(MockHttpSession session, long taskId) throws Exception {
        JsonNode messages = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(session))
                .andExpect(status().isOk())
                .andReturn())
                .get("messages");
        assertThat(messages.size()).as("a message to delete").isPositive();
        return messages.get(0).get("id").asLong();
    }

    private static long idByName(JsonNode list, String name) {
        for (JsonNode one : list) {
            if (name.equals(one.get("name").asText())) {
                return one.get("id").asLong();
            }
        }
        return -1;
    }

    @Test
    void clientContactDetailsAreOnlyVisibleToManagersAndAdmins() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession admin = login("admin", "Admin@12345");
        MockHttpSession associate = login("employee", "Employee@12345");

        mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Acme Corporation"))
                .andExpect(jsonPath("$.data[0].email").value("jane@acme.example"));

        mockMvc.perform(get("/api/clients").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("jane@acme.example"));

        // Withheld fields are left out of the payload rather than sent as nulls.
        mockMvc.perform(get("/api/clients").session(associate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Acme Corporation"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].phone").doesNotExist());

        // A task the associate may read: the client name shows, the contact block does not.
        JsonNode clients = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        long acmeId = clients.get(0).get("id").asLong();

        long taskId = createTask(manager, """
                {"title":"Client detail visibility probe","priority":"NORMAL","clientId":%d}"""
                .formatted(acmeId));
        long associateId = idOf(mockMvc.perform(get("/api/me").session(associate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/task/" + taskId + "/assign")
                        .session(associate)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + associateId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/task/" + taskId).session(associate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.client.name").value("Acme Corporation"))
                .andExpect(jsonPath("$.data.clientDetails").value(nullValue()));

        mockMvc.perform(get("/api/task/" + taskId).session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientDetails.email").value("jane@acme.example"));
    }

    @Test
    void clientCanEscalateAndTheConversationStaysBetweenClientAndManagers() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");
        MockHttpSession associate = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        // A fresh Acme task the associate is assigned to, so every party may read it.
        JsonNode clients = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        long acmeId = -1;
        for (JsonNode one : clients) {
            if ("Acme Corporation".equals(one.get("name").asText())) {
                acmeId = one.get("id").asLong();
            }
        }
        assertThat(acmeId).as("seeded Acme client").isPositive();

        long associateId = idOf(mockMvc.perform(get("/api/me").session(associate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long taskId = createTask(manager, """
                {"title":"Escalation visibility probe","priority":"NORMAL","clientId":%d}"""
                .formatted(acmeId));
        mockMvc.perform(post("/api/task/" + taskId + "/assign")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + associateId + "}"))
                .andExpect(status().isOk());

        // A regular comment sits in the normal thread first.
        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Working on it\",\"internal\":false}"))
                .andExpect(status().isCreated());

        // The client escalates: 201, message flagged as escalation.
        mockMvc.perform(post("/api/task/" + taskId + "/escalate")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"We need this reviewed before Friday\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.escalation").value(true));

        // The task is flagged and its priority raised from NORMAL to URGENT.
        JsonNode clientDetail = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(clientDetail.get("escalated").asBoolean()).isTrue();
        assertThat(clientDetail.get("escalationParticipant").asBoolean()).isTrue();
        assertThat(clientDetail.get("task").get("priority").asText()).isEqualTo("URGENT");
        assertThat(clientDetail.get("escalations").size()).isEqualTo(1);
        assertThat(clientDetail.get("messages").size())
                .as("the escalation never appears in the regular thread")
                .isEqualTo(1);
        assertThat(clientDetail.get("messages").get(0).get("messageBody").asText())
                .isEqualTo("Working on it");

        // The manager sees the same conversation and replies into it.
        JsonNode managerDetail = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(manager))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(managerDetail.get("escalations").size()).isEqualTo(1);
        assertThat(managerDetail.get("escalationParticipant").asBoolean()).isTrue();

        mockMvc.perform(post("/api/task/" + taskId + "/escalate")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Looking into it now\"}"))
                .andExpect(status().isCreated());

        // The client reads the manager's reply - the conversation runs both ways.
        JsonNode afterReply = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(afterReply.get("escalations").size()).isEqualTo(2);
        assertThat(afterReply.get("escalations").get(1).get("messageBody").asText())
                .isEqualTo("Looking into it now");

        // An associate may read the task and its normal thread, but never the conversation.
        JsonNode associateDetail = dataOf(mockMvc.perform(get("/api/task/" + taskId).session(associate))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(associateDetail.get("escalated").asBoolean())
                .as("the escalation flag itself is not a secret")
                .isTrue();
        assertThat(associateDetail.get("escalationParticipant").asBoolean()).isFalse();
        assertThat(associateDetail.get("escalations").size()).isZero();
        assertThat(associateDetail.get("messages").size()).isEqualTo(1);
    }

    @Test
    void escalationIsRestrictedToClientsAndManagers() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");
        MockHttpSession associate = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        // Staff below manager cannot escalate, even though they may read the task.
        long associateTask = createTaskForEmployee(manager, associate);
        mockMvc.perform(post("/api/task/" + associateTask + "/escalate")
                        .session(associate)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Please look at this\"}"))
                .andExpect(status().isForbidden());

        long coordinatorTask = createTask(manager, """
                {"title":"Coordinator escalation gate probe","priority":"NORMAL"}""");
        long coordinatorId = idOf(mockMvc.perform(get("/api/me").session(coordinator))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/task/" + coordinatorTask + "/assign")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + coordinatorId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/task/" + coordinatorTask + "/escalate")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Raising this to you\"}"))
                .andExpect(status().isForbidden());

        // Managers may only reply - they cannot open an escalation themselves.
        long fresh = createTask(manager, """
                {"title":"Manager escalation gate probe","priority":"NORMAL"}""");
        mockMvc.perform(post("/api/task/" + fresh + "/escalate")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Manager trying to escalate\"}"))
                .andExpect(status().isBadRequest());

        // A client only reaches tasks of its own customer.
        long globexTask = taskIdByTitle(manager, "Draft Q3 capacity plan for the support team");
        mockMvc.perform(post("/api/task/" + globexTask + "/escalate")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Not visible to this client\"}"))
                .andExpect(status().isForbidden());

        // Blank bodies are rejected by validation.
        long acmeTask = taskIdByTitle(manager, "Prepare quarterly security review for Acme");
        mockMvc.perform(post("/api/task/" + acmeTask + "/escalate")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notificationsAreScopedToTheViewer() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession associate = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");
        MockHttpSession client = login("client", "Client@12345");

        long taskId = createTask(manager, """
                {"title":"Notification probe task",
                 "description":"Opening line that mirrors the thread",
                 "priority":"NORMAL"}""");
        long associateId = idOf(mockMvc.perform(get("/api/me").session(associate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/task/" + taskId + "/assign")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + associateId + "}"))
                .andExpect(status().isOk());

        // Associates cannot comment at all any more, so a teammate writes the reply.
        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Started on the notification probe\",\"internal\":false}"))
                .andExpect(status().isCreated());

        // The creator hears about the comment; the author does not hear about themself.
        JsonNode managerFeed = notifications(manager);
        assertThat(findNotification(managerFeed, "MESSAGE", taskId))
                .as("creator sees a new comment on a task they created")
                .isNotNull();
        assertThat(findNotification(managerFeed, "ASSIGNED", taskId)).isNull();

        JsonNode associateFeed = notifications(associate);
        assertThat(findNotification(associateFeed, "ASSIGNED", taskId))
                .as("the assignee hears about the assignment")
                .isNotNull();
        assertThat(findNotification(associateFeed, "MESSAGE", taskId))
                .as("the assignee hears about a teammate's comment")
                .isNotNull();
        assertThat(findNotification(notifications(coordinator), "MESSAGE", taskId))
                .as("the comment was written by the coordinator themself")
                .isNull();
        assertThat(feedSnippetContains(associateFeed, "Opening line that mirrors the thread"))
                .as("the task description mirrored into the thread is not an event")
                .isFalse();

        // A client escalates its own task: manager and client both hear about it.
        long acmeTask = taskIdByTitle(manager, "Prepare quarterly security review for Acme");
        mockMvc.perform(post("/api/task/" + acmeTask + "/escalate")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"We need a date confirmed\"}"))
                .andExpect(status().isCreated());

        assertThat(findNotification(notifications(manager), "ESCALATION", acmeTask))
                .as("managers are notified of every escalation")
                .isNotNull();

        // The manager answers inside the same conversation: that is what the client hears about.
        mockMvc.perform(post("/api/task/" + acmeTask + "/escalate")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Confirming the date with legal\"}"))
                .andExpect(status().isCreated());

        JsonNode clientFeed = notifications(client);
        assertThat(findNotification(clientFeed, "ESCALATION", acmeTask))
                .as("the escalating client is notified of the manager's reply")
                .isNotNull();
        assertThat(feedSnippetContains(clientFeed, "We need a date confirmed"))
                .as("your own escalation is not a notification about you")
                .isFalse();

        // Staff below manager never see escalation events, only their own feed.
        JsonNode associateAfterEscalation = notifications(associate);
        assertThat(findNotification(associateAfterEscalation, "ESCALATION", acmeTask))
                .as("associates are not escalation participants")
                .isNull();
        assertThat(feedSnippetContains(associateAfterEscalation, "We need a date confirmed"))
                .as("escalation bodies stay out of the associate feed")
                .isFalse();
        assertThat(feedSnippetContains(associateAfterEscalation, "Confirming the date with legal"))
                .as("escalation bodies stay out of the associate feed")
                .isFalse();
    }

    @Test
    void updatingTheStatusClearsEveryNotificationAboutThatTask() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession associate = login("employee", "Employee@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");

        long taskId = createTask(manager, """
                {"title":"Status clears the bell",
                 "description":"Opening line for the clearing probe",
                 "priority":"NORMAL"}""");
        long associateId = idOf(mockMvc.perform(get("/api/me").session(associate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/task/" + taskId + "/assign")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + associateId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(coordinator)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"First look at the clearing probe\",\"internal\":false}"))
                .andExpect(status().isCreated());

        // Before the move the assignee hears about the job and the comment,
        // and the creator hears about the comment.
        assertThat(findNotification(notifications(associate), "ASSIGNED", taskId)).isNotNull();
        assertThat(findNotification(notifications(associate), "MESSAGE", taskId)).isNotNull();
        assertThat(findNotification(notifications(manager), "MESSAGE", taskId)).isNotNull();

        mockMvc.perform(patch("/api/task/" + taskId + "/status")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        // Once the status moves, none of it is news any more - for anybody.
        assertThat(findNotification(notifications(associate), "ASSIGNED", taskId))
                .as("the assignment stops being a notification")
                .isNull();
        assertThat(findNotification(notifications(associate), "MESSAGE", taskId))
                .as("the earlier comment stops being a notification")
                .isNull();
        assertThat(findNotification(notifications(manager), "MESSAGE", taskId))
                .as("the creator's feed is cleared by the same move")
                .isNull();

        // Activity written after the move is news again.
        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(manager)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"Written after the move\",\"internal\":false}"))
                .andExpect(status().isCreated());

        assertThat(findNotification(notifications(associate), "MESSAGE", taskId))
                .as("a comment written after the move notifies again")
                .isNotNull();
        assertThat(findNotification(notifications(associate), "ASSIGNED", taskId))
                .as("the cleared assignment stays cleared")
                .isNull();
    }

    private JsonNode notifications(MockHttpSession session) throws Exception {
        return dataOf(mockMvc.perform(get("/api/notifications").session(session))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode findNotification(JsonNode feed, String type, long taskId) {
        for (JsonNode item : feed) {
            if (type.equals(item.get("type").asText()) && item.get("taskId").asLong() == taskId) {
                return item;
            }
        }
        return null;
    }

    private static boolean feedSnippetContains(JsonNode feed, String needle) {
        for (JsonNode item : feed) {
            JsonNode snippet = item.get("snippet");
            if (snippet != null && !snippet.isNull() && snippet.asText().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Creates a task and returns its id, read from the {@code Location} header. */
    private long createTask(MockHttpSession session, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/task")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taskNo").exists())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("Location header of the created task").isNotNull();
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    /**
     * Employees may no longer create tasks, so a manager raises it and the
     * employee claims it (self-assign) to become the assignee.
     */
    private long createTaskForEmployee(MockHttpSession manager, MockHttpSession employee) throws Exception {
        long taskId = createTask(manager, """
                {"title":"Automated smoke test task",
                 "description":"Created by TaskportalApplicationTests",
                 "priority":"NORMAL"}""");

        MvcResult me = mockMvc.perform(get("/api/me").session(employee))
                .andExpect(status().isOk())
                .andReturn();
        long employeeId = idOf(me.getResponse().getContentAsString());

        mockMvc.perform(post("/api/task/" + taskId + "/assign")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + employeeId + "}"))
                .andExpect(status().isOk());

        return taskId;
    }

    /** Reads the {@code data} array out of a list response. */
    private JsonNode dataOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data");
    }

    /** Locates a seeded task by its title through the manager-visible backlog. */
    private long taskIdByTitle(MockHttpSession manager, String title) throws Exception {
        JsonNode tasks = dataOf(mockMvc.perform(get("/api/tasks/all").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode task : tasks) {
            if (title.equals(task.get("title").asText())) {
                return task.get("id").asLong();
            }
        }
        throw new AssertionError("No task titled '%s' in the seeded backlog".formatted(title));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    /** Reads {@code data.id} out of a JSON response body. */
    private long idOf(String json) {
        int index = json.indexOf("\"id\":");
        assertThat(index).as("an id in %s", json).isPositive();
        int start = index + "\"id\":".length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    /* ================================================= operational sheets */

    /**
     * The dashboard sheets are derived from live work and scoped by role: a member
     * is ACTIVE exactly while they hold an IN_PROGRESS task, and each role only
     * reads the sheet of the level it supervises.
     */
    @Test
    void operationalSheetsAreDerivedFromLiveWorkAndScopedByRole() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345");
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession coordinator = login("coordinator", "Coordinator@12345");
        MockHttpSession associate = login("employee", "Employee@12345");
        MockHttpSession client = login("client", "Client@12345");

        // An admin may read every staff sheet.
        for (String role : List.of("MANAGER", "COORDINATOR", "ASSOCIATE")) {
            mockMvc.perform(get("/api/dashboard/team").param("role", role).session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        // A manager reaches the levels below it and nothing above.
        mockMvc.perform(get("/api/dashboard/team").param("role", "COORDINATOR").session(manager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/team").param("role", "ASSOCIATE").session(manager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/team").param("role", "MANAGER").session(manager))
                .andExpect(status().isForbidden());

        // A coordinator only supervises associates.
        mockMvc.perform(get("/api/dashboard/team").param("role", "ASSOCIATE").session(coordinator))
                .andExpect(status().isOk());
        for (String role : List.of("MANAGER", "COORDINATOR")) {
            mockMvc.perform(get("/api/dashboard/team").param("role", role).session(coordinator))
                    .andExpect(status().isForbidden());
        }

        // Associates and clients have no sheet at all, and CLIENT is not a sheet role.
        for (MockHttpSession session : List.of(associate, client)) {
            for (String role : List.of("MANAGER", "COORDINATOR", "ASSOCIATE", "CLIENT")) {
                mockMvc.perform(get("/api/dashboard/team").param("role", role).session(session))
                        .andExpect(status().isForbidden());
            }
        }

        // Nobody is assigned an IN_PROGRESS task in the seed, so the manager is IDLE.
        JsonNode managers = sheet(admin, "MANAGER");
        assertThat(managers).as("the seeded manager appears on its own sheet").isNotEmpty();
        JsonNode managerRow = rowFor(managers, "manager");
        assertThat(managerRow.get("presence").asText()).isEqualTo("IDLE");
        assertThat(managerRow.get("currentTask").isNull())
                .as("an idle member carries no current task").isTrue();

        // 'employee' holds an IN_PROGRESS task from the seed, so it is ACTIVE on it.
        JsonNode associates = sheet(admin, "ASSOCIATE");
        JsonNode employeeRow = rowFor(associates, "employee");
        assertThat(employeeRow.get("presence").asText()).isEqualTo("ACTIVE");
        assertThat(employeeRow.get("currentTask").get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(employeeRow.get("since").asText()).as("an active member says since when").isNotBlank();

        // 'employee2' was only ever given an OPEN task, so it stays IDLE.
        JsonNode idleRow = rowFor(associates, "employee2");
        assertThat(idleRow.get("presence").asText()).isEqualTo("IDLE");
        assertThat(idleRow.get("since").isNull()).as("an idle member has no since stamp").isTrue();
    }

    /** Every sheet row names its member, so the sheets are never anonymous. */
    @Test
    void everyOperationalSheetRowIdentifiesItsMember() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345");

        for (String role : List.of("MANAGER", "COORDINATOR", "ASSOCIATE")) {
            JsonNode sheet = sheet(admin, role);
            for (JsonNode row : sheet) {
                assertThat(row.get("role").asText()).isEqualTo(role);
                assertThat(row.get("user").get("username").asText()).isNotBlank();
                assertThat(row.get("presence").asText()).isIn("ACTIVE", "IDLE");
            }
        }
    }

    private JsonNode sheet(MockHttpSession session, String role) throws Exception {
        return dataOf(mockMvc.perform(get("/api/dashboard/team").param("role", role).session(session))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode rowFor(JsonNode sheet, String username) {
        for (JsonNode row : sheet) {
            if (username.equals(row.get("user").get("username").asText())) {
                return row;
            }
        }
        throw new AssertionError("No sheet row for '%s' in %s".formatted(username, sheet));
    }

    /* ================================================== client details page */

    /**
     * One endpoint, three payloads. The commercial block goes to managers/admins
     * and to a customer reading itself; internal notes stay internal; everybody
     * else gets identity only, with the withheld fields absent rather than null.
     */
    @Test
    void clientDetailsPayloadIsShapedByTheCallersRole() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession client = login("client", "Client@12345");
        MockHttpSession associate = login("employee", "Employee@12345");

        JsonNode staff = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        long acmeId = idByName(staff, "Acme Corporation");
        long globexId = idByName(staff, "Globex Industries");

        mockMvc.perform(get("/api/clients/" + acmeId + "/profile").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client.notes").exists())
                .andExpect(jsonPath("$.data.client.industry").value("Manufacturing"))
                .andExpect(jsonPath("$.data.client.paymentStatus").exists())
                .andExpect(jsonPath("$.data.taskStats.total").isNumber())
                .andExpect(jsonPath("$.data.recentTasks").isArray());

        // The customer reads its own record commercially but not the internal notes.
        mockMvc.perform(get("/api/clients/" + acmeId + "/profile").session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client.notes").doesNotExist())
                .andExpect(jsonPath("$.data.client.paymentStatus").exists())
                .andExpect(jsonPath("$.data.client.organizationName").value("Acme Corporation, Inc."));

        // A customer asking for somebody else is indistinguishable from a bad id.
        mockMvc.perform(get("/api/clients/" + globexId + "/profile").session(client))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clients/999999/profile").session(client))
                .andExpect(status().isNotFound());

        // Associates get the name and nothing else — the commercial block is absent.
        String raw = mockMvc.perform(get("/api/clients/" + acmeId + "/profile").session(associate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client.name").value("Acme Corporation"))
                .andReturn().getResponse().getContentAsString();
        JsonNode summary = dataOf(mockMvc.perform(get("/api/clients/" + acmeId + "/profile")
                .session(associate)).andExpect(status().isOk()).andReturn());
        assertThat(summary.get("client").has("notes"))
                .as("notes must be withheld, not sent as null: %s", raw).isFalse();
        assertThat(summary.get("client").has("paymentStatus"))
                .as("the commercial block must be withheld, not sent as null").isFalse();
        assertThat(summary.get("client").has("industry")).isFalse();
    }

    /* ============================================ client-raised tasks */

    /** A client may raise work, but only against its own customer and unassigned. */
    @Test
    void clientCanRaiseATaskOnlyAgainstItsOwnCustomerAndUnassigned() throws Exception {
        MockHttpSession client = login("client", "Client@12345");
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession associate = login("employee", "Employee@12345");

        JsonNode catalogue = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        long acmeId = idByName(catalogue, "Acme Corporation");
        long globexId = idByName(catalogue, "Globex Industries");

        long associateId = idOf(mockMvc.perform(get("/api/me").session(associate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // The happy path: raised, pinned to Acme, left for the delivery team.
        long taskId = createTask(client, """
                {"title":"Client raised request","description":"Please review our renewal terms.",
                 "priority":"URGENT"}""");
        mockMvc.perform(get("/api/task/" + taskId).session(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.client.name").value("Acme Corporation"))
                .andExpect(jsonPath("$.data.task.assignedTo").value(nullValue()))
                .andExpect(jsonPath("$.data.task.status").value("OPEN"))
                .andExpect(jsonPath("$.data.task.priority").value("URGENT"));

        // Picking an assignee is refused outright rather than silently dropped.
        mockMvc.perform(post("/api/task")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Client tries to assign","priority":"NORMAL","assignedToId":%d}"""
                                .formatted(associateId)))
                .andExpect(status().isForbidden());

        // So is filing against another customer, including its own id spelled out.
        mockMvc.perform(post("/api/task")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Client targets a rival","priority":"NORMAL","clientId":%d}"""
                                .formatted(globexId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/task")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Client targets itself","priority":"NORMAL","clientId":%d}"""
                                .formatted(acmeId)))
                .andExpect(status().isCreated());

        // The other roles are unchanged: associates and coordinators still cannot originate work.
        mockMvc.perform(post("/api/task")
                        .session(associate)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Associate tries to originate","priority":"NORMAL"}"""))
                .andExpect(status().isForbidden());
    }
}
