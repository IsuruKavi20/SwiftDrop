package com.swiftdrop.backend.identity.security;

import com.swiftdrop.backend.identity.service.UserDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every logged-in person who calls SwiftDrop is in app_users, without any module having to remember it.
 * Runs after Spring Security has checked the access token (a plain servlet filter, ordered after the security chain).
 * Usually just one quick lookup by id; the first request of a new person copies them from their token.
 * Service and merchant-software tokens (their "sub" is a client id, not a person) are skipped.
 */
@Component
public class CallerCopyFilter extends OncePerRequestFilter {

    private final UserDirectory userDirectory;

    CallerCopyFilter(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        if (login != null && login.getPrincipal() instanceof Jwt token && isPerson(token)) {
            userDirectory.caller(token);
        }
        chain.doFilter(request, response);
    }

    // A person's token has their user id (a UUID) as "sub"; client-credentials tokens have the client id
    private static boolean isPerson(Jwt token) {
        try {
            UUID.fromString(token.getSubject());
            return true;
        } catch (IllegalArgumentException | NullPointerException notAPerson) {
            return false;
        }
    }
}
