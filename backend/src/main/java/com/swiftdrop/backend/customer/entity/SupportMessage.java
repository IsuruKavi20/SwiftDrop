package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "customer_support_message")
public class SupportMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Column(name = "sender_role", nullable = false, length = 15)
    private String senderRole = "CUSTOMER";

    @Column(name = "message_text", nullable = false, length = 2000)
    private String messageText;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {}

    public SupportMessage(String messageText) { this.messageText = messageText; }

    void setTicket(SupportTicket ticket) { this.ticket = ticket; }
    public Long getId() { return id; }
    public String getSenderRole() { return senderRole; }
    public String getMessageText() { return messageText; }
    public Instant getCreatedAt() { return createdAt; }
}
