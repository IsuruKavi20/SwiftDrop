package com.swiftdrop.backend.courier.entity;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.identity.entity.AppUser;
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

/**
 * Audit record created when a courier cannot successfully complete a delivery attempt.
 */
@Entity
@Table(name = "delivery_failure")
public class DeliveryFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failure_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false, updatable = false)
    private AppUser courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", nullable = false, length = 50)
    private DeliveryFailureReason failureReason;

    @Column(name = "failure_remarks", nullable = false, columnDefinition = "TEXT")
    private String failureRemarks;

    @CreationTimestamp
    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    protected DeliveryFailure() {
        // for JPA
    }

    public DeliveryFailure(Delivery delivery, AppUser courier, DeliveryFailureReason failureReason, String failureRemarks) {
        this.delivery = delivery;
        this.courier = courier;
        this.failureReason = failureReason;
        this.failureRemarks = failureRemarks;
    }

    public Long getId() {
        return id;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public AppUser getCourier() {
        return courier;
    }

    public DeliveryFailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(DeliveryFailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureRemarks() {
        return failureRemarks;
    }

    public void setFailureRemarks(String failureRemarks) {
        this.failureRemarks = failureRemarks;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }
}
