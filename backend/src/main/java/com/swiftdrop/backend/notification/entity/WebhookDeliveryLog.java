package com.swiftdrop.backend.notification.entity;

import com.swiftdrop.backend.customer.entity.Delivery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "webhook_delivery_log")
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_config_id", nullable = false)
    private MerchantWebhookConfig webhookConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookStatus status = WebhookStatus.RETRYING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    protected WebhookDeliveryLog() {
        // JPA
    }

    public WebhookDeliveryLog(MerchantWebhookConfig webhookConfig, Delivery delivery, String eventType, String payload) {
        this.webhookConfig = webhookConfig;
        this.delivery = delivery;
        this.eventType = eventType;
        this.payload = payload;
        this.attemptCount = 1;
        this.status = WebhookStatus.RETRYING;
        this.lastAttemptAt = Instant.now();
    }

    public void markSuccess(int statusCode) {
        this.httpStatusCode = statusCode;
        this.status = WebhookStatus.SUCCESS;
        this.lastAttemptAt = Instant.now();
        this.errorMessage = null;
    }

    public void recordFailure(Integer statusCode, String errorMessage, boolean canRetry) {
        this.httpStatusCode = statusCode;
        this.errorMessage = errorMessage;
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.status = canRetry ? WebhookStatus.RETRYING : WebhookStatus.FAILED;
    }

    public Long getId() { return id; }
    public MerchantWebhookConfig getWebhookConfig() { return webhookConfig; }
    public Delivery getDelivery() { return delivery; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Integer getHttpStatusCode() { return httpStatusCode; }
    public int getAttemptCount() { return attemptCount; }
    public WebhookStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
}

