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

import com.swiftdrop.backend.identity.AppUser;

import java.time.Instant;

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

    /** The courier or admin who recorded the event; null for system events. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", updatable = false)
    private AppUser recordedBy;

    protected TrackingEvent() {
        // for JPA
    }

    public TrackingEvent(DeliveryStatus status, String description, String locationName, Instant eventTime, AppUser recordedBy) {
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
    public AppUser getRecordedBy() { return recordedBy; }
}
