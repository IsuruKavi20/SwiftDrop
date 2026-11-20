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
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Column(name = "recipient_contact", nullable = false, length = 150)
    private String recipientContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 30)
    private RecipientRole recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationLog() {
        // JPA
    }

    public NotificationLog(Delivery delivery, String recipientContact, RecipientRole recipientRole,
                           NotificationChannel channel, String eventType, String subject, String content) {
        this.delivery = delivery;
        this.recipientContact = recipientContact;
        this.recipientRole = recipientRole;
        this.channel = channel;
        this.eventType = eventType;
        this.subject = subject;
        this.content = content;
        this.status = NotificationStatus.PENDING;
    }

    public void markSent(Instant sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    public String getRecipientContact() { return recipientContact; }
    public RecipientRole getRecipientRole() { return recipientRole; }
    public NotificationChannel getChannel() { return channel; }
    public String getEventType() { return eventType; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public NotificationStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}

