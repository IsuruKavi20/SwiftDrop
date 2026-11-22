package com.swiftdrop.backend.shared.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests (no Spring, no database): every kind of failure becomes the same safe RFC 9457 answer, and nothing
 * internal (SQL, class names, stack traces) ever reaches the caller.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @AfterEach
    void clearRequestId() {
        MDC.clear();
    }

    @Test
    void conflictKeepsTheModulesMessage() {
        ProblemDetail problem = handler.conflict(new ConflictException("Already registered"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Already registered");
        assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
    }

    @Test
    void aDatabaseRaceBecomesAFriendly409WithoutTheSql() {
        var race = new DataIntegrityViolationException("insert failed",
                new SQLException("duplicate key value violates unique constraint \"uk_users_email\""));

        ProblemDetail problem = handler.dataConflict(race);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("This conflicts with existing data. Please try again.")
                .doesNotContain("uk_users_email").doesNotContain("duplicate key");
    }

    @Test
    void badInputIs400() {
        ProblemDetail problem = handler.badInput(new IllegalArgumentException("Weight must be positive"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
    }

    @Test
    void forbiddenAndUnauthenticatedUseFixedTextsNotTheExceptionMessage() {
        ProblemDetail forbidden = handler.forbidden(new AccessDeniedException("internal rule xyz"));
        ProblemDetail unauthenticated = handler.unauthenticated(new BadCredentialsException("token signature bad"));

        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getDetail()).isEqualTo(SecurityProblemHandlers.FORBIDDEN_DETAIL);
        assertThat(unauthenticated.getStatus()).isEqualTo(401);
        assertThat(unauthenticated.getDetail()).isEqualTo(SecurityProblemHandlers.UNAUTHENTICATED_DETAIL)
                .doesNotContain("signature");
    }

    @Test
    void aDependencyOutageIs503WithAGenericText() {
        ProblemDetail problem = handler.serviceDown(
                new ServiceUnavailableException("auth directory timed out at 10.0.0.5", new RuntimeException()));

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getDetail()).doesNotContain("10.0.0.5");
        assertThat(problem.getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
    }

    @Test
    void theSafetyNetNeverLeaksInternals() {
        ProblemDetail problem = handler.unexpected(new NullPointerException("AppUser.getEmail() was null"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("AppUser").doesNotContain("null");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void theRequestIdIsAddedWhenOneExists() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-123");

        ProblemDetail problem = handler.notFound(new NotFoundException("Delivery not found"));

        assertThat(problem.getProperties()).containsEntry("requestId", "req-123");
        assertThat(problem.getType()).hasToString("https://swiftdrop.dev/problems/not-found");
    }

    @Test
    void decorateFillsAMissingTitleAndCodeForMapsUnknownStatuses() {
        ProblemDetail unusual = ProblemDetail.forStatus(299);   // a status Spring has no name for

        Problems.decorate(unusual, "ODD_CASE");

        assertThat(unusual.getTitle()).isEqualTo("Error");
        assertThat(Problems.codeFor(299)).isEqualTo("ERROR");
        assertThat(Problems.codeFor(HttpStatus.NOT_FOUND.value())).isEqualTo("NOT_FOUND");
    }

    @Test
    void securityHandlerWritesA403ProblemBody() throws Exception {
        var handlers = new SecurityProblemHandlers(JsonMapper.builder().build());
        var response = new MockHttpServletResponse();

        handlers.handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"")
                .contains(SecurityProblemHandlers.FORBIDDEN_DETAIL);
    }
}
