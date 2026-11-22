package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.identity.client.MerchantTokenIntrospection;
import com.swiftdrop.backend.identity.client.MerchantTokenIntrospection.Result;
import com.swiftdrop.backend.shared.error.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.merchant.security.AuthMerchantMachineTokenContract;
import com.swiftdrop.backend.merchant.security.MerchantMachineTokenContract;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "swiftdrop.merchant.machine-booking.enabled=true")
@AutoConfigureMockMvc
@Transactional
class AuthMerchantBookingTest {
    private static final String CLIENT = "mc_1234567890123456789012";
    private static final String TOKEN = "machine-token-fixture";
    private static final String API = "/api/merchant-integrations/deliveries";
    private static final List<String> BOOKING_PERMISSION = List.of("merchant:api-booking");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MerchantTokenIntrospection introspection;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "true"})
    void environmentFlagControlsProductionAdapter(String value) throws IOException {
        var defaults = new ResourcePropertySource(
                "file:src/main/resources/application.properties");
        java.util.Map<String, Object> variables = value == null ? Map.of()
                : Map.of("SWIFTDROP_MERCHANT_MACHINE_BOOKING_ENABLED", value);
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove("systemEnvironment");
                    sources.addLast(defaults);
                    sources.addFirst(new SystemEnvironmentPropertySource("bookingFixture", variables));
                })
                .withBean(MerchantTokenIntrospection.class, () -> mock(MerchantTokenIntrospection.class))
                .withUserConfiguration(AuthMerchantMachineTokenContract.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if ("true".equals(value)) {
                        assertThat(context).hasSingleBean(MerchantMachineTokenContract.class);
                    } else {
                        assertThat(context).doesNotHaveBean(MerchantMachineTokenContract.class);
                    }
                });
    }

    @Test
    void booksForVerifiedOwnerAndChecksIntrospectionAgainOnReplay() throws Exception {
        UUID owner = UUID.randomUUID();
        mvc.perform(get("/api/merchants/me").with(person(owner))).andExpect(status().isOk());
        when(introspection.inspect(TOKEN)).thenReturn(new Result(true, owner.toString()));
        var caller = machine(owner.toString(), CLIENT, BOOKING_PERMISSION);
        var created = book(caller).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.trackingNumber").isNotEmpty()).andReturn();
        book(caller).andExpect(status().isOk()).andExpect(content().json(created.getResponse().getContentAsString()));
        verify(introspection, times(2)).inspect(TOKEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_api_booking WHERE client_id = ?", Integer.class, CLIENT))
                .isEqualTo(1);
        when(introspection.inspect(TOKEN)).thenReturn(new Result(false, null));
        book(caller).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsHumansMissingPermissionAndMalformedClaimsWithoutIntrospection() throws Exception {
        UUID owner = UUID.randomUUID();
        book(person(owner)).andExpect(status().isForbidden());
        book(machine(owner.toString(), CLIENT, List.of())).andExpect(status().isForbidden());
        book(machine(owner.toString(), CLIENT, List.of("parcel:create", "merchant:api-access"))).andExpect(status().isForbidden());
        book(machine("illegal-owner", CLIENT, BOOKING_PERMISSION)).andExpect(status().isForbidden());
        book(machine(owner.toString(), CLIENT + "x", BOOKING_PERMISSION)).andExpect(status().isForbidden());
        verifyNoInteractions(introspection);
    }

    @Test
    void rejectsMissingProfileAndMissingOrConflictingIntrospectedOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        var caller = machine(owner.toString(), CLIENT, BOOKING_PERMISSION);
        when(introspection.inspect(TOKEN)).thenReturn(new Result(true, owner.toString()),
                new Result(true, null), new Result(true, UUID.randomUUID().toString()));
        book(caller).andExpect(status().isForbidden());
        book(caller).andExpect(status().isForbidden());
        book(caller).andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedBearerTokenBeforeIntrospection() throws Exception {
        mvc.perform(post(API).header("Authorization", "Bearer not-a-jwt")
                        .header("Idempotency-Key", "order-1").contentType(MediaType.APPLICATION_JSON)
                        .content(MerchantDeliveryApiTest.VALID_DELIVERY_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(introspection);
    }

    @Test
    void deniesInactiveTokensAndFailsClosedOnOutage() throws Exception {
        UUID owner = UUID.randomUUID();
        when(introspection.inspect(TOKEN)).thenReturn(new Result(false, null))
                .thenThrow(new ServiceUnavailableException("Introspection unavailable"));
        var caller = machine(owner.toString(), CLIENT, BOOKING_PERMISSION);
        book(caller).andExpect(status().isUnauthorized());
        book(caller).andExpect(status().isServiceUnavailable());
    }

    @Test
    void machineDoesNotProvisionAHumanOrAccessPersonalEndpoints() throws Exception {
        var caller = machine(UUID.randomUUID().toString(), CLIENT,
                List.of("merchant:api-access", "merchant:api-booking", "parcel:create", "parcel:read-own"));
        int users = jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class);
        mvc.perform(get("/api/merchants/me").with(caller)).andExpect(status().isForbidden());
        mvc.perform(get("/api/merchants/me/webhooks").with(caller)).andExpect(status().isForbidden());
        mvc.perform(post("/api/merchants/me/deliveries/1/confirm").with(caller)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(users);
        verifyNoInteractions(introspection);
    }

    private ResultActions book(RequestPostProcessor caller) throws Exception {
        return mvc.perform(post(API).with(caller).header("Idempotency-Key", "order-1")
                .contentType(MediaType.APPLICATION_JSON).content(MerchantDeliveryApiTest.VALID_DELIVERY_JSON));
    }

    private static RequestPostProcessor person(UUID owner) {
        return jwt().jwt(token -> token.subject(owner.toString()).claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("merchant:api-access"));
    }

    private static RequestPostProcessor machine(String owner, String clientId, List<String> permissions) {
        return jwt().jwt(token -> token.tokenValue(TOKEN).subject(CLIENT).claim("client_id", clientId)
                        .claim("merchant_owner", owner).claim("entitlements", permissions))
                .authorities(permissions.stream().map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
    }
}
