package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_support_ticket")
public class SupportTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", updatable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Column(nullable = false, length = 120)
    private String subject;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<SupportMessage> messages = new ArrayList<>();

    protected SupportTicket() {}

    public SupportTicket(Customer customer, Delivery delivery, SupportCategory category, String subject) {
        this.customer = customer;
        this.delivery = delivery;
        this.category = category;
        this.subject = subject;
    }

    public void addMessage(SupportMessage message) {
        message.setTicket(this);
        messages.add(message);
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Delivery getDelivery() { return delivery; }
    public SupportCategory getCategory() { return category; }
    public SupportTicketStatus getStatus() { return status; }
    public String getSubject() { return subject; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SupportMessage> getMessages() { return messages; }
}
