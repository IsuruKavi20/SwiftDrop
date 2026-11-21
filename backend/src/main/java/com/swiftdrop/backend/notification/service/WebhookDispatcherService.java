package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.repository.WebhookDeliveryLogRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final MerchantWebhookConfigRepository configRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final EntityManager entities;

    // Lazily initialized to avoid Java's PipeImpl loopback probe at bean-construction time,
    // which fails in sandboxed / no-network test environments.
    private volatile HttpClient httpClient;

    public WebhookDispatcherService(MerchantWebhookConfigRepository configRepository,
                                    WebhookDeliveryLogRepository deliveryLogRepository, EntityManager entities) {
        this.configRepository = configRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.entities = entities;
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
    public void dispatchWebhook(Long merchantId, Long deliveryId, String eventType, String jsonPayload) {
        dispatchWebhook(merchantId, deliveryId, eventType, jsonPayload, null);
    }

    @Transactional
    public void dispatchWebhook(Long merchantId, Long deliveryId, String eventType, String jsonPayload, String eventId) {
        Delivery delivery = null;
        if (eventId != null) {
            delivery = entities.find(Delivery.class, deliveryId);
            if (delivery == null || !merchantId.equals(delivery.getMerchantId())) {
                return;
            }
        }
        List<MerchantWebhookConfig> configs = configRepository.findByMerchant_IdAndActiveTrue(merchantId);
        if (configs.isEmpty()) {
            log.debug("No active webhook configs for merchant {}", merchantId);
            return;
        }

        for (MerchantWebhookConfig config : configs) {
            if (isSubscribed(config.getSubscribedEvents(), eventType)) {
                sendWithRetry(config, delivery, eventType, jsonPayload, eventId);
            }
        }
    }

    private boolean isSubscribed(String subscribedEvents, String eventType) {
        if (subscribedEvents == null || subscribedEvents.isBlank() || "*".equals(subscribedEvents.trim())) {
            return true;
        }
        for (String ev : subscribedEvents.split(",")) {
            String key = ev.trim();
            if (key.equalsIgnoreCase(eventType)
                    || (MerchantConfirmedWebhook.STATUS.equalsIgnoreCase(eventType) && "DELIVERY_CONFIRMED".equalsIgnoreCase(key))) {
                return true;
            }
        }
        return false;
    }

    private void sendWithRetry(MerchantWebhookConfig config, Delivery delivery, String eventType, String payload,
                               String eventId) {
        WebhookDeliveryLog logEntry = new WebhookDeliveryLog(config, delivery, eventType, payload);
        deliveryLogRepository.save(logEntry);

        String signature = computeHmacSha256(payload, config.getSecretKey());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SsrfValidator.validateStatic(config.getEndpointUrl());
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getEndpointUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-SwiftDrop-Signature", signature)
                        .header("X-SwiftDrop-Event", eventType)
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
                if (eventId != null) {
                    request.header("X-SwiftDrop-Event-Id", eventId);
                }

                HttpResponse<Void> response = getHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    log.info("Webhook configuration {} delivered event {}: status {}", config.getId(), eventType, status);
                    logEntry.markSuccess(status);
                    deliveryLogRepository.save(logEntry);
                    return;
                } else {
                    boolean canRetry = attempt < MAX_ATTEMPTS;
                    log.warn("Webhook attempt {} for configuration {} returned HTTP {}", attempt, config.getId(), status);
                    logEntry.recordFailure(status, "Received HTTP status " + status, canRetry);
                    deliveryLogRepository.save(logEntry);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Webhook delivery interrupted", interrupted);
            } catch (Exception ex) {
                boolean canRetry = attempt < MAX_ATTEMPTS;
                log.warn("Webhook attempt {} for configuration {} failed ({})", attempt, config.getId(),
                        ex.getClass().getSimpleName());
                logEntry.recordFailure(null, "Webhook request failed", canRetry);
                deliveryLogRepository.save(logEntry);
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    // Backoff delay before retry
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Webhook retry interrupted", interrupted);
                }
            }
        }
    }

    public static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
