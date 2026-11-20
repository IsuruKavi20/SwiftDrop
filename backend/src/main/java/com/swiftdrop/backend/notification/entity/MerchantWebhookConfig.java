package com.swiftdrop.backend.notification.entity;

import com.swiftdrop.backend.merchant.entity.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "merchant_webhook_config")
public class MerchantWebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_config_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;

    @Column(name = "secret_key", nullable = false, length = 128)
    private String secretKey;

    @Column(name = "subscribed_events", nullable = false, length = 255)
    private String subscribedEvents;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantWebhookConfig() {
        // JPA
    }

    public MerchantWebhookConfig(Merchant merchant, String endpointUrl, String secretKey, String subscribedEvents) {
        this.merchant = merchant;
        this.endpointUrl = endpointUrl;
        this.secretKey = secretKey;
        this.subscribedEvents = subscribedEvents;
        this.active = true;
    }

    public Long getId() { return id; }
    public Merchant getMerchant() { return merchant; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getSubscribedEvents() { return subscribedEvents; }
    public void setSubscribedEvents(String subscribedEvents) { this.subscribedEvents = subscribedEvents; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

