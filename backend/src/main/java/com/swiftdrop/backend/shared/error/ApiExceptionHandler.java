package com.swiftdrop.backend.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every error from every module into the same JSON (see {@link Problems}). Modules just throw:
 * <ul>
 *   <li>{@link ServiceUnavailableException} → 503 SERVICE_UNAVAILABLE (details only in the log)</li>
 *   <li>{@link NotFoundException} → 404 NOT_FOUND, {@link ConflictException} → 409 CONFLICT</li>
 *   <li>{@link IllegalArgumentException} → 400 BAD_REQUEST; an invalid {@code @Valid} body → 400 VALIDATION_FAILED
 *       with {@code errors: {"field": "message"}}</li>
 *   <li>{@link AccessDeniedException} (also from {@code @PreAuthorize}) → 403 FORBIDDEN</li>
 *   <li>anything else → 500 INTERNAL_ERROR with a fixed sentence; the details go only to the log</li>
 * </ul>
 * Spring's own errors (405, 415, unreadable JSON, ...) get the same shape via {@link #handleExceptionInternal}.
 * 401/403 decided before a controller runs come from {@link SecurityProblemHandlers}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return Problems.of(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return Problems.of(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    /** Two requests racing past the service checks hit a unique constraint (e.g. same email at the same moment). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(DataIntegrityViolationException exception) {
        log.warn("Data conflict: {}", exception.getMostSpecificCause().getMessage());
        return Problems.of(HttpStatus.CONFLICT, "CONFLICT", "This conflicts with existing data. Please try again.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badInput(IllegalArgumentException exception) {
        return Problems.of(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException exception) {
        return Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", SecurityProblemHandlers.FORBIDDEN_DETAIL);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated(AuthenticationException exception) {
        return Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", SecurityProblemHandlers.UNAUTHENTICATED_DETAIL);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail serviceDown(ServiceUnavailableException exception) {
        log.error("Dependency unavailable", exception);
        return Problems.of(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "A service we depend on is not available right now. Please try again in a moment.");
    }

    /** The safety net: never send stack traces, SQL or class names to the caller. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again; if it keeps happening, report the requestId.");
    }

    /** 400 with {"errors": {"pickup.contactPhone": "must be 9 to 15 digits...", ...}} for the form to show. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Spring's built-in errors (405, 415, unreadable body, missing parameter, unknown path, ...): same shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Problems.decorate(problem, Problems.codeFor(problem.getStatus()));
        }
        return response;
    }
}
