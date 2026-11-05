package com.swiftdrop.backend.customer.entity;

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

import java.time.Instant;
import java.util.UUID;

/** One step in a delivery's tracking timeline. Add it through {@link Delivery#addTrackingEvent}. */
@Entity
@Table(name = "tracking_event")
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tracking_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DeliveryStatus status;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "location_name", updatable = false)
    private String locationName;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    /** User id (auth service) of the courier or admin who recorded the event; null for system events. */
    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    protected TrackingEvent() {
        // for JPA
    }

    public TrackingEvent(DeliveryStatus status, String description, String locationName, Instant eventTime, UUID recordedBy) {
        this.status = status;
        this.description = description;
        this.locationName = locationName;
        this.eventTime = eventTime;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    void setDelivery(Delivery delivery) { this.delivery = delivery; }
    public DeliveryStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getLocationName() { return locationName; }
    public Instant getEventTime() { return eventTime; }
    public UUID getRecordedBy() { return recordedBy; }
}
