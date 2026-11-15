package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.notification.dto.CreateWebhookRequest;
import com.swiftdrop.backend.notification.dto.UpdateWebhookRequest;
import com.swiftdrop.backend.notification.dto.WebhookConfigResponse;
import com.swiftdrop.backend.notification.dto.WebhookDeliveryLogResponse;
import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.repository.WebhookDeliveryLogRepository;
import com.swiftdrop.backend.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MerchantWebhookConfigRepository configRepository;
    private final WebhookDeliveryLogRepository logRepository;
    private final EntityManager entityManager;
    private final SsrfValidator ssrfValidator;

    private volatile HttpClient httpClient;

    @Autowired
    public MerchantWebhookService(MerchantWebhookConfigRepository configRepository,
                                  WebhookDeliveryLogRepository logRepository,
                                  EntityManager entityManager,
                                  SsrfValidator ssrfValidator) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.entityManager = entityManager;
        this.ssrfValidator = Objects.requireNonNull(ssrfValidator, "ssrfValidator must not be null");
    }

    public MerchantWebhookService(MerchantWebhookConfigRepository configRepository,
                                  WebhookDeliveryLogRepository logRepository,
                                  EntityManager entityManager) {
        this(configRepository, logRepository, entityManager, new SsrfValidator());
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                }
            }
        }
        return httpClient;
    }

    @Transactional
    public List<WebhookConfigResponse> getWebhooksForOwner(AppUser owner) {
        Merchant merchant = resolveMerchant(owner);
        return configRepository.findByMerchant_IdOrderByCreatedAtDesc(merchant.getId())
                .stream()
                .map(WebhookConfigResponse::from)
                .toList();
    }

    @Transactional
    public WebhookConfigResponse getWebhookForOwner(AppUser owner, Long webhookId) {
        Merchant merchant = resolveMerchant(owner);
        MerchantWebhookConfig config = configRepository.findByIdAndMerchant_Id(webhookId, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found: " + webhookId));
        return WebhookConfigResponse.from(config);
    }

    @Transactional
    public WebhookConfigResponse createWebhook(AppUser owner, CreateWebhookRequest request) {
        Merchant merchant = resolveMerchant(owner);
        String endpointUrl = request.endpointUrl().trim();
        ssrfValidator.validate(endpointUrl);

        String secretKey = (request.secretKey() != null && !request.secretKey().isBlank())
                ? request.secretKey().trim()
                : generateSecretKey();

        MerchantWebhookConfig config = new MerchantWebhookConfig(
                merchant,
                endpointUrl,
                secretKey,
                request.subscribedEvents().trim()
        );
        MerchantWebhookConfig saved = configRepository.save(config);
        log.info("Created webhook config {} for merchant {}", saved.getId(), merchant.getId());
        return WebhookConfigResponse.from(saved);
    }

    @Transactional
    public WebhookConfigResponse updateWebhook(AppUser owner, Long webhookId, UpdateWebhookRequest request) {
        Merchant merchant = resolveMerchant(owner);
        MerchantWebhookConfig config = configRepository.findByIdAndMerchant_Id(webhookId, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found: " + webhookId));

        String endpointUrl = request.endpointUrl().trim();
        ssrfValidator.validate(endpointUrl);

        config.setEndpointUrl(endpointUrl);
        config.setSubscribedEvents(request.subscribedEvents().trim());

        if (request.active() != null) {
            config.setActive(request.active());
        }

        if (request.secretKey() != null && !request.secretKey().isBlank()) {
            config.setSecretKey(request.secretKey().trim());
        }

        MerchantWebhookConfig updated = configRepository.save(config);
        log.info("Updated webhook config {} for merchant {}", updated.getId(), merchant.getId());
        return WebhookConfigResponse.from(updated);
    }

    @Transactional
    public void deleteWebhook(AppUser owner, Long webhookId) {
        Merchant merchant = resolveMerchant(owner);
        MerchantWebhookConfig config = configRepository.findByIdAndMerchant_Id(webhookId, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found: " + webhookId));
        configRepository.delete(config);
        log.info("Deleted webhook config {} for merchant {}", webhookId, merchant.getId());
    }

    @Transactional
    public List<WebhookDeliveryLogResponse> getLogsForWebhook(AppUser owner, Long webhookId) {
        Merchant merchant = resolveMerchant(owner);
        // Verify ownership first
        configRepository.findByIdAndMerchant_Id(webhookId, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found: " + webhookId));

        return logRepository.findByWebhookConfig_IdOrderByCreatedAtDesc(webhookId)
                .stream()
                .map(WebhookDeliveryLogResponse::from)
                .toList();
    }

    @Transactional
    public List<WebhookDeliveryLogResponse> getRecentLogsForOwner(AppUser owner) {
        Merchant merchant = resolveMerchant(owner);
        return logRepository.findTop50ByWebhookConfig_Merchant_IdOrderByCreatedAtDesc(merchant.getId())
                .stream()
                .map(WebhookDeliveryLogResponse::from)
                .toList();
    }

    @Transactional
    public WebhookDeliveryLogResponse testWebhook(AppUser owner, Long webhookId) {
        Merchant merchant = resolveMerchant(owner);
        MerchantWebhookConfig config = configRepository.findByIdAndMerchant_Id(webhookId, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found: " + webhookId));

        String testPayload = "{\"event\":\"webhook.test\",\"timestamp\":\"" + Instant.now() + "\",\"message\":\"SwiftDrop webhook test ping\"}";
        WebhookDeliveryLog logEntry = new WebhookDeliveryLog(config, null, "webhook.test", testPayload);
        logRepository.save(logEntry);

        String signature = WebhookDispatcherService.computeHmacSha256(testPayload, config.getSecretKey());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpointUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-SwiftDrop-Signature", signature)
                    .header("X-SwiftDrop-Event", "webhook.test")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(testPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                logEntry.markSuccess(status);
            } else {
                logEntry.recordFailure(status, "Received HTTP status " + status, false);
            }
        } catch (Exception ex) {
            log.warn("Test ping to {} failed: {}", config.getEndpointUrl(), ex.getMessage());
            logEntry.recordFailure(null, ex.getMessage(), false);
        }

        WebhookDeliveryLog saved = logRepository.save(logEntry);
        return WebhookDeliveryLogResponse.from(saved);
    }

    public static String generateSecretKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Merchant resolveMerchant(AppUser owner) {
        List<Merchant> list = entityManager.createQuery(
                        "SELECT m FROM Merchant m WHERE m.owner.id = :ownerId", Merchant.class)
                .setParameter("ownerId", owner.getId())
                .getResultList();

        if (list.isEmpty()) {
            Merchant created = new Merchant(owner);
            entityManager.persist(created);
            entityManager.flush();
            return created;
        }
        return list.get(0);
    }
}
