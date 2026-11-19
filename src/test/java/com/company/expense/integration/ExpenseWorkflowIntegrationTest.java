package com.company.expense.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class ExpenseWorkflowIntegrationTest {

    private static final String PASSWORD = "secret-pass";
    private static final String ADMIN = "admin@test.com";
    private static final String MANAGER = "lead@company.com";
    private static final String EMPLOYEE = "asha@company.com";
    private static final String FINANCE = "fin@company.com";
    private static final byte[] PDF = "%PDF-1.4 test receipt".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    static Path receiptDirectory;

    @DynamicPropertySource
    static void receiptStorage(DynamicPropertyRegistry registry) {
        registry.add("app.receipts.storage-directory", receiptDirectory::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    private long departmentId;
    private long travelId;
    private long mealsId;

    @BeforeEach
    void setUpOrganisation() throws Exception {
        departmentId = id(perform(ADMIN, post("/api/admin/departments"),
                "{\"name\": \"Engineering\", \"code\": \"ENG\"}").andExpect(status().isCreated()));
        long managerId = createUser(MANAGER, "MANAGER", departmentId, null);
        createUser(EMPLOYEE, "EMPLOYEE", departmentId, managerId);
        createUser(FINANCE, "FINANCE", null, null);

        travelId = id(perform(ADMIN, post("/api/admin/categories"),
                "{\"name\": \"Travel\", \"requiresReceipt\": true}").andExpect(status().isCreated()));
        mealsId = id(perform(ADMIN, post("/api/admin/categories"),
                "{\"name\": \"Meals\", \"requiresReceipt\": false}").andExpect(status().isCreated()));

        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Finance review above 500", "minAmount": 500.01, "priority": 1,
                 "steps": [{"approverType": "FINANCE_ROLE"}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.steps[0].stepOrder").value(1));
    }

    @Test
    void expenseGoesThroughRejectionResubmissionAndFullApproval() throws Exception {
        long expenseId = createExpense(travelId, "1200.00");

        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A receipt is required for category Travel"));

        mockMvc.perform(multipart("/api/expenses/{id}/receipts", expenseId)
                        .file(new MockMultipartFile("file", "notes.txt", "application/pdf", "hello".getBytes()))
                        .with(httpBasic(EMPLOYEE, PASSWORD)))
                .andExpect(status().isUnprocessableContent());
        long receiptId = uploadPdf(expenseId);

        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approvalSteps", hasSize(2)))
                .andExpect(jsonPath("$.approvalSteps[0].approverType").value("MANAGER"))
                .andExpect(jsonPath("$.approvalSteps[1].approverType").value("FINANCE_ROLE"));

        perform(FINANCE, post("/api/approvals/{id}/approve", expenseId)).andExpect(status().isNotFound());
        perform(FINANCE, get("/api/approvals")).andExpect(jsonPath("$", hasSize(0)));

        perform(MANAGER, get("/api/approvals"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].expenseId").value(expenseId))
                .andExpect(jsonPath("$[0].employeeName").value("Test User"));
        perform(MANAGER, get("/api/expenses/{id}/receipts/{receiptId}", expenseId, receiptId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(PDF));

        perform(MANAGER, post("/api/approvals/{id}/reject", expenseId), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        perform(MANAGER, post("/api/approvals/{id}/reject", expenseId), "{\"reason\": \"Add the client name\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approvalSteps[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.approvalSteps[0].comment").value("Add the client name"))
                .andExpect(jsonPath("$.approvalSteps[1].status").value("SKIPPED"));

        perform(EMPLOYEE, put("/api/expenses/{id}", expenseId), expenseJson(travelId, "1200.00", "Client visit: Acme"))
                .andExpect(status().isOk());
        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(jsonPath("$.approvalSteps", hasSize(4)))
                .andExpect(jsonPath("$.approvalSteps[2].submissionNo").value(2));

        perform(MANAGER, post("/api/approvals/{id}/approve", expenseId), "{\"comment\": \"OK now\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        perform(MANAGER, post("/api/approvals/{id}/approve", expenseId))
                .andExpect(status().isForbidden());

        perform(FINANCE, get("/api/approvals")).andExpect(jsonPath("$", hasSize(1)));
        perform(FINANCE, post("/api/approvals/{id}/approve", expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalSteps[3].decidedByName").value("Test User"));

        perform(EMPLOYEE, get("/api/expenses/{id}", expenseId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalSteps[*].status")
                        .value(contains("REJECTED", "SKIPPED", "APPROVED", "APPROVED")));
        perform(EMPLOYEE, get("/api/expenses").param("status", "APPROVED"))
                .andExpect(jsonPath("$", hasSize(1)));

        perform(FINANCE, get("/api/finance/expenses").param("departmentId", String.valueOf(departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalAmount").value(1200.00))
                .andExpect(jsonPath("$.expenses[0].department").value("Engineering"));
        perform(FINANCE, get("/api/finance/expenses/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("approved-expenses.csv")))
                .andExpect(content().string(startsWith("expense_id,employee,department,category")))
                .andExpect(content().string(containsString(expenseId + ",Test User,Engineering,Travel")));

        perform(ADMIN, get("/api/admin/audit-logs")
                        .param("entityType", "EXPENSE")
                        .param("entityId", String.valueOf(expenseId)))
                .andExpect(jsonPath("$[*].action").value(contains(
                        "CREATE", "SUBMIT", "REJECT", "RESUBMIT", "APPROVE", "APPROVE")));
        perform(ADMIN, get("/api/admin/audit-logs"))
                .andExpect(jsonPath("$[0].action").value("EXPORT"))
                .andExpect(jsonPath("$[0].entityType").value("EXPENSE_REPORT"));
    }

    @Test
    void smallExpenseNeedsOnlyManagerApproval() throws Exception {
        long expenseId = createExpense(mealsId, "250.00");

        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(jsonPath("$.approvalSteps", hasSize(1)));
        perform(MANAGER, post("/api/approvals/{id}/approve", expenseId))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        perform(EMPLOYEE, put("/api/expenses/{id}", expenseId), expenseJson(mealsId, "1.00", "Changed"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyOneOpenExpensePerCategory() throws Exception {
        long first = createExpense(mealsId, "120.00");
        perform(EMPLOYEE, post("/api/expenses"), expenseJson(mealsId, "80.00", "Dinner"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("#" + first)));
        long travel = id(perform(EMPLOYEE, post("/api/expenses"), expenseJson(travelId, "900.00", "Cab"))
                .andExpect(status().isCreated()));
        perform(EMPLOYEE, put("/api/expenses/{id}", travel), expenseJson(mealsId, "900.00", "Cab"))
                .andExpect(status().isUnprocessableContent());

        perform(EMPLOYEE, post("/api/expenses/{id}/submit", first)).andExpect(status().isOk());
        perform(EMPLOYEE, post("/api/expenses"), expenseJson(mealsId, "80.00", "Dinner"))
                .andExpect(status().isUnprocessableContent());

        perform(MANAGER, post("/api/approvals/{id}/reject", first), "{\"reason\": \"Wrong date\"}")
                .andExpect(status().isOk());
        long replacement = createExpense(mealsId, "80.00");
        perform(EMPLOYEE, post("/api/expenses/{id}/submit", first))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("#" + replacement)));
    }

    @Test
    void rejectsMalformedEmail() throws Exception {
        perform(ADMIN, post("/api/admin/users"), """
                {"email": "asha@company", "password": "%s", "fullName": "Asha", "role": "FINANCE"}
                """.formatted(PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("must look like name@company.com"));
    }

    @Test
    void managerNamedByRuleIsNotAskedTwice() throws Exception {
        long managerId = ((Number) JsonPath.read(perform(MANAGER, get("/api/auth/me"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Meals sign-off", "categoryId": %d, "priority": 2,
                 "steps": [{"approverType": "SPECIFIC_USER", "approverUserId": %d}]}
                """.formatted(mealsId, managerId))
                .andExpect(status().isCreated());
        long expenseId = createExpense(mealsId, "120.00");

        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(jsonPath("$.approvalSteps", hasSize(1)))
                .andExpect(jsonPath("$.approvalSteps[0].approverType").value("MANAGER"));
        perform(MANAGER, post("/api/approvals/{id}/approve", expenseId))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rolesAndOwnershipAreEnforced() throws Exception {
        long expenseId = createExpense(travelId, "900.00");
        long receiptId = uploadPdf(expenseId);
        createUser("ravi@company.com", "EMPLOYEE", departmentId, null);

        perform("ravi@company.com", get("/api/expenses/{id}", expenseId)).andExpect(status().isNotFound());
        perform("ravi@company.com", post("/api/expenses/{id}/submit", expenseId)).andExpect(status().isNotFound());
        perform(ADMIN, get("/api/expenses/{id}/receipts/{receiptId}", expenseId, receiptId))
                .andExpect(status().isNotFound());

        perform(EMPLOYEE, get("/api/finance/expenses")).andExpect(status().isForbidden());
        perform(EMPLOYEE, get("/api/approvals")).andExpect(status().isForbidden());
        perform(ADMIN, post("/api/expenses"), expenseJson(mealsId, "10.00", "Lunch"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeExpenseGoesToAdminAndThenAnotherFinanceUser() throws Exception {
        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Meals review", "categoryId": %d, "priority": 5, "steps": [{"approverType": "FINANCE_ROLE"}]}
                """.formatted(mealsId)).andExpect(status().isCreated());
        long expenseId = id(perform(FINANCE, post("/api/expenses"), expenseJson(mealsId, "300.00", "Team lunch"))
                .andExpect(status().isCreated()));

        perform(FINANCE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no other active finance user")));

        createUser("fin2@company.com", "FINANCE", null, null);
        perform(FINANCE, post("/api/expenses/{id}/submit", expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalSteps[*].approverType", contains("ADMIN_ROLE", "FINANCE_ROLE")));

        perform(ADMIN, get("/api/approvals")).andExpect(jsonPath("$[*].expenseId", contains((int) expenseId)));
        perform(ADMIN, post("/api/approvals/{id}/approve", expenseId)).andExpect(status().isOk());

        perform(FINANCE, get("/api/approvals")).andExpect(jsonPath("$", hasSize(0)));
        perform(FINANCE, post("/api/approvals/{id}/approve", expenseId)).andExpect(status().isForbidden());
        perform("fin2@company.com", post("/api/approvals/{id}/approve", expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void adminCanEditRule() throws Exception {
        long ruleId = id(perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Travel", "categoryId": %d, "priority": 7, "steps": [{"approverType": "FINANCE_ROLE"}]}
                """.formatted(travelId)).andExpect(status().isCreated()));
        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Other", "priority": 8, "steps": [{"approverType": "FINANCE_ROLE"}]}
                """).andExpect(status().isCreated());
        long managerId = ((Number) JsonPath.read(perform(MANAGER, get("/api/auth/me"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        perform(ADMIN, put("/api/admin/approval-rules/{id}", ruleId), """
                {"name": "Big travel", "categoryId": %d, "minAmount": 5000, "priority": 6,
                 "steps": [{"approverType": "SPECIFIC_USER", "approverUserId": %d}, {"approverType": "FINANCE_ROLE"}]}
                """.formatted(travelId, managerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Big travel"))
                .andExpect(jsonPath("$.priority").value(6))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.steps[*].approverType", contains("SPECIFIC_USER", "FINANCE_ROLE")));

        perform(ADMIN, put("/api/admin/approval-rules/{id}", ruleId), """
                {"name": "Big travel", "priority": 8, "steps": [{"approverType": "FINANCE_ROLE"}]}
                """).andExpect(status().isConflict());
    }

    @Test
    void validatesExpenseInput() throws Exception {
        perform(EMPLOYEE, post("/api/expenses"), """
                {"categoryId": %d, "amount": 10.123, "expenseDate": "%s", "description": ""}
                """.formatted(mealsId, LocalDate.now().plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.expenseDate").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void rejectsSecondActiveRuleWithSamePriority() throws Exception {
        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Clash", "priority": 1, "steps": [{"approverType": "FINANCE_ROLE"}]}
                """)
                .andExpect(status().isConflict());
        perform(ADMIN, post("/api/admin/approval-rules"), """
                {"name": "Bad step", "priority": 2, "steps": [{"approverType": "MANAGER"}]}
                """)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void financeReportRejectsInvertedDateRange() throws Exception {
        perform(FINANCE, get("/api/finance/expenses").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void draftCanBeCleanedUpButSubmittedExpenseCannot() throws Exception {
        long draftId = createExpense(travelId, "900.00");
        long receiptId = uploadPdf(draftId);

        perform(EMPLOYEE, get("/api/expenses/{id}", draftId))
                .andExpect(jsonPath("$.receiptRequired").value(true))
                .andExpect(jsonPath("$.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.permissions.canDelete").value(true))
                .andExpect(jsonPath("$.permissions.canDecide").value(false));
        perform(EMPLOYEE, delete("/api/expenses/{id}/receipts/{receiptId}", draftId, receiptId))
                .andExpect(status().isNoContent());
        perform(EMPLOYEE, get("/api/expenses/{id}", draftId)).andExpect(jsonPath("$.receipts", hasSize(0)));
        perform(EMPLOYEE, delete("/api/expenses/{id}", draftId)).andExpect(status().isNoContent());
        perform(EMPLOYEE, get("/api/expenses/{id}", draftId)).andExpect(status().isNotFound());

        long submittedId = createExpense(mealsId, "120.00");
        perform(EMPLOYEE, post("/api/expenses/{id}/submit", submittedId)).andExpect(status().isOk());
        perform(EMPLOYEE, delete("/api/expenses/{id}", submittedId)).andExpect(status().isUnprocessableContent());
        perform(MANAGER, get("/api/expenses/{id}", submittedId))
                .andExpect(jsonPath("$.permissions.canEdit").value(false))
                .andExpect(jsonPath("$.permissions.canDecide").value(true));
        perform(MANAGER, delete("/api/expenses/{id}", submittedId)).andExpect(status().isNotFound());
    }

    @Test
    void approversSeeTheirDecisionHistory() throws Exception {
        long expenseId = createExpense(mealsId, "120.00");
        perform(EMPLOYEE, post("/api/expenses/{id}/submit", expenseId));
        perform(MANAGER, post("/api/approvals/{id}/reject", expenseId), "{\"reason\": \"Personal lunch\"}");

        perform(MANAGER, get("/api/approvals/history"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].expenseId").value(expenseId))
                .andExpect(jsonPath("$[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$[0].comment").value("Personal lunch"))
                .andExpect(jsonPath("$[0].expenseStatus").value("REJECTED"));
        perform(EMPLOYEE, get("/api/approvals/history")).andExpect(status().isForbidden());
    }

    @Test
    void financeGetsFilterOptions() throws Exception {
        perform(FINANCE, get("/api/finance/expenses/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0].name").value("Engineering"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.employees", hasSize(2)))
                .andExpect(jsonPath("$.employees[0].detail").value("Engineering"));
    }

    @Test
    void securityErrorsAreProblemDetailsAndWebUiIsPublic() throws Exception {
        mockMvc.perform(get("/api/expenses").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        perform(EMPLOYEE, get("/api/finance/expenses"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
        mockMvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }

    private long createExpense(long categoryId, String amount) throws Exception {
        return id(perform(EMPLOYEE, post("/api/expenses"), expenseJson(categoryId, amount, "Client visit"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currencyCode").value("INR")));
    }

    private long uploadPdf(long expenseId) throws Exception {
        return id(mockMvc.perform(multipart("/api/expenses/{id}/receipts", expenseId)
                        .file(new MockMultipartFile("file", "../../ticket.pdf", "text/plain", PDF))
                        .with(httpBasic(EMPLOYEE, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("ticket.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf")));
    }

    private long createUser(String email, String role, Long departmentId, Long managerId) throws Exception {
        return id(perform(ADMIN, post("/api/admin/users"), """
                {"email": "%s", "password": "%s", "fullName": "Test User",
                 "role": "%s", "departmentId": %s, "managerId": %s}
                """.formatted(email, PASSWORD, role, departmentId, managerId))
                .andExpect(status().isCreated()));
    }

    private static String expenseJson(long categoryId, String amount, String description) {
        return """
                {"categoryId": %d, "amount": %s, "expenseDate": "%s", "description": "%s"}
                """.formatted(categoryId, amount, LocalDate.now(), description);
    }

    private ResultActions perform(String email, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(httpBasic(email, email.equals(ADMIN) ? "admin-pass-123" : PASSWORD)));
    }

    private ResultActions perform(String email, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(email, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
