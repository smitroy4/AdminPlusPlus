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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
                                {"title":"Associates must not raise tasks","priority":"LOW"}"""))
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
                                {"title":"Coordinators must not raise tasks","priority":"LOW"}"""))
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

    @Test
    void employeeCanCommentOnATaskTheyClaimed() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");

        long taskId = createTaskForEmployee(manager, employee);

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(employee)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"First reply from the test suite","internal":false}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/task/" + taskId).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.title").value("Automated smoke test task"))
                .andExpect(jsonPath("$.data.task.taskNo").exists())
                .andExpect(jsonPath("$.data.messages.length()").value(2));
    }

    @Test
    void validationFailuresReturn400WithAMessage() throws Exception {
        MockHttpSession session = login("manager", "Manager@12345");

        mockMvc.perform(post("/api/task")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"no title","priority":"LOW"}"""))
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
                                {"title":"Cookie only must be rejected","priority":"LOW"}"""))
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
                                {"title":"Nothing delivered must be rejected","priority":"LOW"}"""))
                .andExpect(status().isForbidden());
    }

    /** {@code internal} must stay optional: omitting it must not fail deserialisation. */
    @Test
    void messageWithoutTheInternalFlagIsAccepted() throws Exception {
        MockHttpSession manager = login("manager", "Manager@12345");
        MockHttpSession employee = login("employee", "Employee@12345");

        long taskId = createTaskForEmployee(manager, employee);

        mockMvc.perform(post("/api/task/" + taskId + "/message")
                        .session(employee)
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

        // Read-only: no commenting, no reassigning.
        mockMvc.perform(post("/api/task/" + acmeTask + "/message")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageBody":"A client should not get this far","internal":false}"""))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/task/" + acmeTask + "/assign")
                        .session(client)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isForbidden());
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

        mockMvc.perform(get("/api/clients").session(associate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Acme Corporation"))
                .andExpect(jsonPath("$.data[0].email").value(nullValue()))
                .andExpect(jsonPath("$.data[0].phone").value(nullValue()));

        // A task the associate may read: the client name shows, the contact block does not.
        JsonNode clients = dataOf(mockMvc.perform(get("/api/clients").session(manager))
                .andExpect(status().isOk())
                .andReturn());
        long acmeId = clients.get(0).get("id").asLong();

        long taskId = createTask(manager, """
                {"title":"Client detail visibility probe","priority":"LOW","clientId":%d}"""
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
                 "priority":"HIGH"}""");

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
}
