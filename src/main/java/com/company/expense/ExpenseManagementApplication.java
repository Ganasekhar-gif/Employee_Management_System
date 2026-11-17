package com.company.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExpenseManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseManagementApplication.class, args);
    }
}
