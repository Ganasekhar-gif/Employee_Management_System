package com.company.expense.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes 401 and 403 as RFC 9457 problem details, like every other API error.
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // Browsers show their own login dialog when a 401 carries this header, which would hide the web UI's form.
    private static final String AJAX_HEADER = "X-Requested-With";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (request.getHeader(AJAX_HEADER) == null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"expense-management\"");
        }
        write(response, HttpStatus.UNAUTHORIZED, "Sign in with a valid email and password");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Your role does not allow this action");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
