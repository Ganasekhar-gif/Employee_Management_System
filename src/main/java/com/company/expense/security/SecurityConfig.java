package com.company.expense.security;

import com.company.expense.enums.RoleName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data: blob:; "
            + "frame-src blob:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ProblemDetailSecurityHandler problemHandler = new ProblemDetailSecurityHandler();
        String employee = RoleName.EMPLOYEE.name();
        String manager = RoleName.MANAGER.name();
        String finance = RoleName.FINANCE.name();
        String admin = RoleName.ADMIN.name();
        return http
                // No session cookie is issued, so there is nothing for CSRF to ride on.
                // Revisit if the API moves to cookie-based sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg", "/css/**", "/js/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole(admin)
                        .requestMatchers("/api/finance/**").hasRole(finance)
                        .requestMatchers("/api/approvals/**").hasAnyRole(manager, finance, admin)
                        .requestMatchers(HttpMethod.POST, "/api/expenses/**").hasAnyRole(employee, manager, finance)
                        .requestMatchers(HttpMethod.PUT, "/api/expenses/**").hasAnyRole(employee, manager, finance)
                        .requestMatchers(HttpMethod.DELETE, "/api/expenses/**").hasAnyRole(employee, manager, finance)
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
