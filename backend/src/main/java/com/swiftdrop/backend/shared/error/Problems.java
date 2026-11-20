package com.swiftdrop.backend.shared.error;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Locale;


public final class Problems {

    private static final String TYPE_BASE = "https://swiftdrop.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return decorate(problem, code);
    }

    /** Adds type, code and requestId to a problem (also to the ones Spring builds itself, e.g. 405 or 415). */
    public static ProblemDetail decorate(ProblemDetail problem, String code) {
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        if (problem.getTitle() == null) {
            HttpStatus known = HttpStatus.resolve(problem.getStatus());
            problem.setTitle(known != null ? known.getReasonPhrase() : "Error");
        }
        problem.setProperty("code", code);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    /** NOT_FOUND, BAD_REQUEST, ... from the HTTP status, for errors that have no more specific code. */
    public static String codeFor(int status) {
        HttpStatus known = HttpStatus.resolve(status);
        return known != null ? known.name() : "ERROR";
    }
}
