package com.swiftdrop.backend.notification.controller;

import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.service.DnsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MerchantWebhookApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantWebhookConfigRepository webhookRepository;

    @MockitoBean
    private DnsResolver dnsResolver;

    private static final byte[] PUBLIC_IPV4 = new byte[]{(byte) 93, (byte) 184, (byte) 216, 34};

    @BeforeEach
    void setUpDnsResolver() throws Exception {
        lenient().when(dnsResolver.resolve(anyString()))
                .thenAnswer(invocation -> {
                    String host = invocation.getArgument(0, String.class);
                    return resolveForTest(host);
                });
    }

    private InetAddress[] resolveForTest(String host) throws UnknownHostException {
        if (host == null) {
            throw new UnknownHostException("Host is null");
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "localhost" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})};
            case "127.0.0.1" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})};
            case "0.0.0.0" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{0, 0, 0, 0})};
            case "10.0.0.1" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{10, 0, 0, 1})};
            case "192.168.1.50" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{(byte) 192, (byte) 168, 1, 50})};
            case "169.254.169.254" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{(byte) 169, (byte) 254, (byte) 169, (byte) 254})};
            case "rebinding-private.attacker.com" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{10, 0, 0, 99})};
            case "rebinding-zero.attacker.com" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{0, 0, 0, 0})};
            case "rebinding-loopback.attacker.com" -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})};
            case "internal.lan" -> throw new UnknownHostException("Host not found: " + host);
            default -> {
                if (lower.startsWith("127.") || lower.startsWith("10.") || lower.startsWith("192.168.") || lower.startsWith("172.")) {
                    yield new InetAddress[]{InetAddress.getByName(host)};
                }
                yield new InetAddress[]{InetAddress.getByAddress(host, PUBLIC_IPV4)};
            }
        };
    }

    @Test
    void unauthenticatedCall_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/merchants/me/webhooks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonMerchantUser_returnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("customer:profile-manage"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAndListWebhooks_success() throws Exception {
        UUID merchantOwnerId = UUID.randomUUID();

        String payload = """
                {
                  "endpointUrl": "https://api.merchantstore.com/webhooks/swiftdrop",
                  "subscribedEvents": "DELIVERY_STATUS_CHANGED,DELIVERY_CONFIRMED",
                  "secretKey": "my-secure-webhook-secret-key-12345"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.endpointUrl").value("https://api.merchantstore.com/webhooks/swiftdrop"))
                .andExpect(jsonPath("$.subscribedEvents").value("DELIVERY_STATUS_CHANGED,DELIVERY_CONFIRMED"))
                .andExpect(jsonPath("$.secretKey").value("my-secure-webhook-secret-key-12345"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long webhookId = createdNode.get("id").asLong();

        // List webhooks
        mockMvc.perform(get("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(webhookId));

        // Get single webhook
        mockMvc.perform(get("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(webhookId))
                .andExpect(jsonPath("$.endpointUrl").value("https://api.merchantstore.com/webhooks/swiftdrop"));

        // Update webhook
        String updatePayload = """
                {
                  "endpointUrl": "https://api.merchantstore.com/webhooks/updated",
                  "subscribedEvents": "*",
                  "active": false
                }
                """;

        mockMvc.perform(put("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpointUrl").value("https://api.merchantstore.com/webhooks/updated"))
                .andExpect(jsonPath("$.subscribedEvents").value("*"))
                .andExpect(jsonPath("$.active").value(false));

        // Delete webhook
        mockMvc.perform(delete("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWebhook_autoGeneratesSecretWhenOmitted() throws Exception {
        UUID merchantOwnerId = UUID.randomUUID();

        String payload = """
                {
                  "endpointUrl": "https://callback.partner.com/events",
                  "subscribedEvents": "DELIVERY_DELIVERED"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretKey").isNotEmpty())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String secret = node.get("secretKey").asText();
        assertThat(secret).hasSize(64);
    }

    @Test
    void ssrfProtection_blocksLocalhostAndPrivateIps() throws Exception {
        UUID merchantOwnerId = UUID.randomUUID();

        String[] forbiddenUrls = new String[]{
                "http://localhost:8080/webhook",
                "http://127.0.0.1:8080/callback",
                "http://169.254.169.254/latest/meta-data",
                "http://10.0.0.1/admin",
                "http://192.168.1.50/hook",
                "http://internal.lan/hook"
        };

        for (String url : forbiddenUrls) {
            String payload = String.format("""
                    {
                      "endpointUrl": "%s",
                      "subscribedEvents": "*"
                    }
                    """, url);

            mockMvc.perform(post("/api/merchants/me/webhooks")
                            .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                    .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void ssrfProtection_blocksDomainResolvingToPrivateOrLoopbackIp() throws Exception {
        UUID merchantOwnerId = UUID.randomUUID();

        String[] forbiddenUrls = new String[]{
                "https://rebinding-private.attacker.com/webhook",
                "https://rebinding-loopback.attacker.com/webhook"
        };

        for (String url : forbiddenUrls) {
            String payload = String.format("""
                    {
                      "endpointUrl": "%s",
                      "subscribedEvents": "*"
                    }
                    """, url);

            mockMvc.perform(post("/api/merchants/me/webhooks")
                            .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                    .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void ssrfProtection_blocksDomainResolvingToZeroAddress() throws Exception {
        UUID merchantOwnerId = UUID.randomUUID();

        String payload = """
                {
                  "endpointUrl": "https://rebinding-zero.attacker.com/webhook",
                  "subscribedEvents": "*"
                }
                """;

        mockMvc.perform(post("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(merchantOwnerId.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantIsolation_cannotAccessAnotherMerchantsWebhook() throws Exception {
        UUID merchant1Owner = UUID.randomUUID();
        UUID merchant2Owner = UUID.randomUUID();

        String payload = """
                {
                  "endpointUrl": "https://m1.com/hook",
                  "subscribedEvents": "*"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/merchants/me/webhooks")
                        .with(jwt().jwt(builder -> builder.subject(merchant1Owner.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        long webhookId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Merchant 2 tries to GET Merchant 1's webhook -> 404 Not Found
        mockMvc.perform(get("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchant2Owner.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isNotFound());

        // Merchant 2 tries to DELETE Merchant 1's webhook -> 404 Not Found
        mockMvc.perform(delete("/api/merchants/me/webhooks/" + webhookId)
                        .with(jwt().jwt(builder -> builder.subject(merchant2Owner.toString()))
                                .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isNotFound());
    }
}
