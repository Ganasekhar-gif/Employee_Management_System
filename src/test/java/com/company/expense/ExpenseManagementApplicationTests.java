package com.company.expense;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(EmbeddedPostgresConfiguration.class)
class ExpenseManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
