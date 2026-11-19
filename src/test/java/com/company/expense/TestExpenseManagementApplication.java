package com.company.expense;

import com.company.expense.config.EmbeddedPostgresConfiguration;
import org.springframework.boot.SpringApplication;

public class TestExpenseManagementApplication {

    public static void main(String[] args) {
        System.setProperty("app.embedded-postgres.data-directory",
                System.getProperty("app.embedded-postgres.data-directory", "data/postgres"));
        SpringApplication.from(ExpenseManagementApplication::main)
                .with(EmbeddedPostgresConfiguration.class)
                .run(args);
    }
}
