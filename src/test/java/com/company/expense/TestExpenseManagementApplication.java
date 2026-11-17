package com.company.expense;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import org.springframework.boot.SpringApplication;

public class TestExpenseManagementApplication {

    public static void main(String[] args) {
        SpringApplication.from(ExpenseManagementApplication::main)
                .with(EmbeddedPostgresConfiguration.class)
                .run(args);
    }
}
