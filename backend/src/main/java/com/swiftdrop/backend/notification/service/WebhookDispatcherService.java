package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.repository.WebhookDeliveryLogRepository;
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

    // Lazily initialized to avoid Java's PipeImpl loopback probe at bean-construction time,
    // which fails in sandboxed / no-network test environments.
    private volatile HttpClient httpClient;

    public WebhookDispatcherService(MerchantWebhookConfigRepository configRepository,
                                    WebhookDeliveryLogRepository deliveryLogRepository) {
        this.configRepository = configRepository;
        this.deliveryLogRepository = deliveryLogRepository;
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
        List<MerchantWebhookConfig> configs = configRepository.findByMerchant_IdAndActiveTrue(merchantId);
        if (configs.isEmpty()) {
            log.debug("No active webhook configs for merchant {}", merchantId);
            return;
        }

        for (MerchantWebhookConfig config : configs) {
            if (isSubscribed(config.getSubscribedEvents(), eventType)) {
                sendWithRetry(config, deliveryId, eventType, jsonPayload);
            }
        }
    }

    private boolean isSubscribed(String subscribedEvents, String eventType) {
        if (subscribedEvents == null || subscribedEvents.isBlank() || "*".equals(subscribedEvents.trim())) {
            return true;
        }
        for (String ev : subscribedEvents.split(",")) {
            if (ev.trim().equalsIgnoreCase(eventType.trim())) {
                return true;
            }
        }
        return false;
    }

    private void sendWithRetry(MerchantWebhookConfig config, Long deliveryId, String eventType, String payload) {
        WebhookDeliveryLog logEntry = new WebhookDeliveryLog(config, null, eventType, payload);
        deliveryLogRepository.save(logEntry);

        String signature = computeHmacSha256(payload, config.getSecretKey());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getEndpointUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-SwiftDrop-Signature", signature)
                        .header("X-SwiftDrop-Event", eventType)
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    log.info("Webhook delivered successfully to {} for event {}: status {}", config.getEndpointUrl(), eventType, status);
                    logEntry.markSuccess(status);
                    deliveryLogRepository.save(logEntry);
                    return;
                } else {
                    boolean canRetry = attempt < MAX_ATTEMPTS;
                    log.warn("Webhook attempt {} to {} returned non-2xx status: {}", attempt, config.getEndpointUrl(), status);
                    logEntry.recordFailure(status, "Received HTTP status " + status, canRetry);
                    deliveryLogRepository.save(logEntry);
                }
            } catch (Exception ex) {
                boolean canRetry = attempt < MAX_ATTEMPTS;
                log.warn("Webhook attempt {} to {} failed: {}", attempt, config.getEndpointUrl(), ex.getMessage());
                logEntry.recordFailure(null, ex.getMessage(), canRetry);
                deliveryLogRepository.save(logEntry);
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    // Backoff delay before retry
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
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
