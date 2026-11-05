package com.swiftdrop.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail for every module: each endpoint of our API must say WHO may call it with
 * {@code @PreAuthorize("hasAuthority('<permission>')")} on the method or its controller class (see docs/AUTH.md).
 * The only exception is the public area {@code /api/public/**}, which SecurityConfig opens on purpose.
 *
 * <p>Logged-in users already need a valid token everywhere (SecurityConfig). This test catches the other mistake:
 * an endpoint any logged-in user can call, whatever their role (e.g. a customer reaching a dispatcher action).
 */
@SpringBootTest
class EndpointSecurityTest {

    private static final String OUR_CODE = "com.swiftdrop.backend";
    private static final String PUBLIC_AREA = "/api/public/";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void everyEndpointHasAPermissionRuleOrIsPublic() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> ourEndpoints = mappings.getHandlerMethods().entrySet().stream()
                .filter(endpoint -> endpoint.getValue().getBeanType().getPackageName().startsWith(OUR_CODE))
                .toList();
        // Guard against a silently empty check (e.g. a renamed package): we know the customer module has endpoints
        assertThat(ourEndpoints).as("no endpoints found to check").isNotEmpty();

        List<String> unprotected = ourEndpoints.stream()
                .filter(endpoint -> !isPublic(endpoint.getKey()))
                .filter(endpoint -> !hasPermissionRule(endpoint.getValue()))
                .map(EndpointSecurityTest::describe)
                .sorted()
                .toList();

        assertThat(unprotected)
                .as("These endpoints have no @PreAuthorize permission rule. Add one (see docs/AUTH.md section 3), "
                        + "or move them under /api/public/ if they really are public")
                .isEmpty();
    }

    private static boolean isPublic(RequestMappingInfo mapping) {
        return !mapping.getPatternValues().isEmpty()
                && mapping.getPatternValues().stream().allMatch(path -> path.startsWith(PUBLIC_AREA));
    }

    private static boolean hasPermissionRule(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PreAuthorize.class);
    }

    private static String describe(Map.Entry<RequestMappingInfo, HandlerMethod> endpoint) {
        HandlerMethod method = endpoint.getValue();
        return endpoint.getKey().getMethodsCondition().getMethods() + " " + endpoint.getKey().getPatternValues()
                + " -> " + method.getBeanType().getSimpleName() + "." + method.getMethod().getName() + "()";
    }
}
