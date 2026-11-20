package com.swiftdrop.backend.courier.entity;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.identity.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Confirmation evidence captured when a courier marks a delivery as DELIVERED.
 */
@Entity
@Table(name = "delivery_proof")
public class DeliveryProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proof_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, unique = true, updatable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false, updatable = false)
    private AppUser courier;

    /** Type of proof e.g. SIGNATURE, PHOTO, OTP */
    @Column(name = "proof_type", nullable = false, length = 50)
    private String proofType;

    /** Name of the person who received and signed for the parcel */
    @Column(name = "recipient_sign_name", nullable = false, length = 100)
    private String recipientSignName;

    @Column(name = "delivery_notes", columnDefinition = "TEXT")
    private String deliveryNotes;

    /** Optional reference/URL to photo or signature evidence */
    @Column(name = "proof_media_url", length = 2048)
    private String proofMediaUrl;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected DeliveryProof() {
        // for JPA
    }

    public DeliveryProof(Delivery delivery, AppUser courier, String proofType, String recipientSignName,
                         String deliveryNotes, String proofMediaUrl) {
        this.delivery = delivery;
        this.courier = courier;
        this.proofType = proofType;
        this.recipientSignName = recipientSignName;
        this.deliveryNotes = deliveryNotes;
        this.proofMediaUrl = proofMediaUrl;
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

    public String getProofType() {
        return proofType;
    }

    public void setProofType(String proofType) {
        this.proofType = proofType;
    }

    public String getRecipientSignName() {
        return recipientSignName;
    }

    public void setRecipientSignName(String recipientSignName) {
        this.recipientSignName = recipientSignName;
    }

    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    public void setDeliveryNotes(String deliveryNotes) {
        this.deliveryNotes = deliveryNotes;
    }

    public String getProofMediaUrl() {
        return proofMediaUrl;
    }

    public void setProofMediaUrl(String proofMediaUrl) {
        this.proofMediaUrl = proofMediaUrl;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
