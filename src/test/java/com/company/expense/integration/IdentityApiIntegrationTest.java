package com.company.expense.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.bootstrap-admin.email=admin@test.com",
        "app.bootstrap-admin.password=admin-pass-123"
})
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfiguration.class)
@Transactional
class IdentityApiIntegrationTest {

    private static final String PASSWORD = "secret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin@test.com", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminCanSignIn() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("ADMIN@test.com", "admin-pass-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void adminSetsUpDepartmentAndEmployeeSignsIn() throws Exception {
        long departmentId = createDepartment("Engineering", "eng");
        long managerId = createUser("lead@company.com", "MANAGER", departmentId, null);
        long employeeId = createUser("asha@company.com", "EMPLOYEE", departmentId, managerId);

        mockMvc.perform(asAdmin(put("/api/admin/departments/{id}/manager", departmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerId\": " + managerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENG"))
                .andExpect(jsonPath("$.managerId").value(managerId));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("asha@company.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.departmentId").value(departmentId))
                .andExpect(jsonPath("$.managerId").value(managerId));
    }

    @Test
    void forbidsNonAdminFromAdminEndpoints() throws Exception {
        long departmentId = createDepartment("Engineering", "ENG");
        createUser("asha@company.com", "EMPLOYEE", departmentId, null);

        mockMvc.perform(get("/api/admin/users").with(httpBasic("asha@company.com", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "fullName": "", "role": "EMPLOYEE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void returnsConflictForDuplicateEmail() throws Exception {
        long departmentId = createDepartment("Engineering", "ENG");
        createUser("asha@company.com", "EMPLOYEE", departmentId, null);

        mockMvc.perform(asAdmin(post("/api/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("ASHA@company.com", "EMPLOYEE", departmentId, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with email asha@company.com already exists"));
    }

    @Test
    void rejectsManagerFromAnotherDepartment() throws Exception {
        long engineeringId = createDepartment("Engineering", "ENG");
        long salesId = createDepartment("Sales", "SALES");
        long salesManagerId = createUser("sales.lead@company.com", "MANAGER", salesId, null);

        mockMvc.perform(asAdmin(post("/api/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("asha@company.com", "EMPLOYEE", engineeringId, salesManagerId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Direct manager must be an active Manager in the same department"));
    }

    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/users/{id}", 999_999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 999999 was not found"));
    }

    private long createDepartment(String name, String code) throws Exception {
        String body = mockMvc.perform(asAdmin(post("/api/admin/departments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"code\": \"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createUser(String email, String role, long departmentId, Long managerId) throws Exception {
        String body = mockMvc.perform(asAdmin(post("/api/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(email, role, departmentId, managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String userJson(String email, String role, long departmentId, Long managerId) {
        return """
                {"email": "%s", "password": "%s", "fullName": "Test User",
                 "role": "%s", "departmentId": %d, "managerId": %s}
                """.formatted(email, PASSWORD, role, departmentId, managerId);
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("admin@test.com", "admin-pass-123"));
    }
}
