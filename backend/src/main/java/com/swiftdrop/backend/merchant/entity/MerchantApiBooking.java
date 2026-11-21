package com.swiftdrop.backend.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "merchant_api_booking")
public class MerchantApiBooking {
    @Id
    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private Long merchantId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MerchantApiBooking() {}

    public MerchantApiBooking(Long deliveryId, Long merchantId, String clientId, String idempotencyKey,
                              String requestHash, Instant createdAt) {
        this.deliveryId = deliveryId;
        this.merchantId = merchantId;
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    public Long getDeliveryId() { return deliveryId; }

    public boolean matchesRequest(String hash) { return requestHash.equals(hash); }
}
