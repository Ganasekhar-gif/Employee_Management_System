package com.company.expense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String fullName) {

    public boolean isConfigured() {
        return password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", fullName=" + fullName + "]";
    }
}
