package com.company.expense.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.bootstrap-admin.email=admin@test.com",
        "app.bootstrap-admin.password=admin-pass-123"
})
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfiguration.class)
@Transactional
class UserLifecycleIntegrationTest {

    private static final String PASSWORD = "secret-pass";

    @Autowired
    private MockMvc mockMvc;

    private long departmentId;
    private long headId;
    private long leadId;
    private long employeeId;

    @BeforeEach
    void setUp() throws Exception {
        departmentId = id(asAdmin(post("/api/admin/departments"), "{\"name\": \"Engineering\", \"code\": \"ENG\"}"));
        headId = createUser("head@company.com", "MANAGER", null);
        leadId = createUser("lead@company.com", "MANAGER", headId);
        employeeId = createUser("asha@company.com", "EMPLOYEE", leadId);
        asAdmin(put("/api/admin/departments/{id}/manager", departmentId), "{\"managerId\": " + headId + "}")
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingManagerMovesReportsToDepartmentManager() throws Exception {
        asAdmin(post("/api/admin/users/{id}/deactivate", leadId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        asAdmin(get("/api/admin/users/{id}", employeeId), null)
                .andExpect(jsonPath("$.managerId").value(headId));
        mockMvc.perform(get("/api/auth/me").with(httpBasic("lead@company.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
        asAdmin(get("/api/admin/audit-logs").param("entityType", "USER").param("entityId", String.valueOf(leadId)), null)
                .andExpect(jsonPath("$[0].action").value("DEACTIVATE"))
                .andExpect(jsonPath("$[0].details.reassignedReports").value(1));

        asAdmin(post("/api/admin/users/{id}/activate", leadId), null)
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/auth/me").with(httpBasic("lead@company.com", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void blocksDeactivationWhenNoReplacementManagerExists() throws Exception {
        asAdmin(post("/api/admin/users/{id}/deactivate", headId), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Assign another active manager to Test User before deactivating Test User"));

        asAdmin(get("/api/admin/users/{id}", headId), null).andExpect(jsonPath("$.active").value(true));
        asAdmin(get("/api/admin/departments"), null).andExpect(jsonPath("$[0].managerId").value(headId));
    }

    @Test
    void blocksDeactivationWhileApprovalsArePending() throws Exception {
        asAdmin(post("/api/admin/categories"), "{\"name\": \"Meals\", \"requiresReceipt\": false}");
        long categoryId = ((Number) JsonPath.read(
                asAdmin(get("/api/admin/categories"), null).andReturn().getResponse().getContentAsString(),
                "$[0].id")).longValue();
        String expense = """
                {"categoryId": %d, "amount": 100, "expenseDate": "%s", "description": "Lunch"}
                """.formatted(categoryId, LocalDate.now());
        long expenseId = id(mockMvc.perform(post("/api/expenses").with(httpBasic("asha@company.com", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content(expense)));
        mockMvc.perform(post("/api/expenses/{id}/submit", expenseId).with(httpBasic("asha@company.com", PASSWORD)))
                .andExpect(status().isOk());

        asAdmin(post("/api/admin/users/{id}/deactivate", leadId), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Test User still has 1 pending approval step(s). They must be decided before the account is deactivated"));
    }

    @Test
    void adminCannotDeactivateThemselves() throws Exception {
        long adminId = ((Number) JsonPath.read(asAdmin(get("/api/auth/me"), null)
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        asAdmin(post("/api/admin/users/{id}/deactivate", adminId), null)
                .andExpect(status().isUnprocessableContent());
    }

    private long createUser(String email, String role, Long managerId) throws Exception {
        return id(asAdmin(post("/api/admin/users"), """
                {"email": "%s", "password": "%s", "fullName": "Test User",
                 "role": "%s", "departmentId": %d, "managerId": %s}
                """.formatted(email, PASSWORD, role, departmentId, managerId)));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(httpBasic("admin@test.com", "admin-pass-123"));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static long id(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
