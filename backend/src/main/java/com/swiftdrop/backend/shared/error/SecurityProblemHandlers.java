package com.swiftdrop.backend.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;


@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHENTICATED_DETAIL = "Please log in: a valid access token is required.";
    static final String FORBIDDEN_DETAIL = "You do not have permission to do this.";

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper json;

    public SecurityProblemHandlers(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        bearerEntryPoint.commence(request, response, failure);
        write(response, Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", UNAUTHENTICATED_DETAIL));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException {
        bearerDenied.handle(request, response, denied);
        write(response, Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", FORBIDDEN_DETAIL));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
