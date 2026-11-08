package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.swiftdrop.backend.identity.entity.AppUser;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * A delivery booking and its current state. Its pickup and destination are {@link DeliveryAddress} rows, its
 * contents are {@link Parcel} rows, and its history is {@link TrackingEvent} rows.
 */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    /** The assigned courier (a person from the auth service); null until a courier is assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private AppUser courier;

    @Column(name = "tracking_number", nullable = false, unique = true, updatable = false, length = 30)
    private String trackingNumber;

    /** Random and unguessable, unlike the tracking number; used for public tracking links. */
    @Column(name = "public_tracking_token", nullable = false, unique = true, updatable = false, length = 64)
    private String publicTrackingToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    private DeliveryType deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Loaded for up to 50 deliveries in one query, so a list page does not run one query per row. */
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<DeliveryAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Parcel> parcels = new ArrayList<>();

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("eventTime ASC")
    private List<TrackingEvent> trackingEvents = new ArrayList<>();

    protected Delivery() {
        // for JPA
    }

    public Delivery(Customer customer, String trackingNumber, String publicTrackingToken, DeliveryType deliveryType) {
        this.customer = customer;
        this.trackingNumber = trackingNumber;
        this.publicTrackingToken = publicTrackingToken;
        this.deliveryType = deliveryType;
    }

    /** Sets the pickup or destination address. Throws if this delivery already has one of that type. */
    public void setAddress(DeliveryAddress address) {
        if (findAddress(address.getAddressType()).isPresent()) {
            // Replacing in one flush would insert before deleting and break the unique (delivery, type) constraint
            throw new IllegalStateException("Delivery already has a " + address.getAddressType() + " address");
        }
        address.setDelivery(this);
        addresses.add(address);
    }

    public Optional<DeliveryAddress> getPickupAddress() {
        return findAddress(AddressType.PICKUP);
    }

    public Optional<DeliveryAddress> getDestinationAddress() {
        return findAddress(AddressType.DESTINATION);
    }

    private Optional<DeliveryAddress> findAddress(AddressType type) {
        return addresses.stream().filter(address -> address.getAddressType() == type).findFirst();
    }

    public void addParcel(Parcel parcel) {
        parcel.setDelivery(this);
        parcels.add(parcel);
    }

    /**
     * Records a tracking event. The delivery's status follows its newest event by event time, so an event
     * recorded late (e.g. after a sync) does not overwrite a more recent status.
     */
    public void addTrackingEvent(TrackingEvent event) {
        boolean newest = trackingEvents.stream().noneMatch(existing -> existing.getEventTime().isAfter(event.getEventTime()));
        event.setDelivery(this);
        trackingEvents.add(event);
        if (newest) {
            status = event.getStatus();
            if (status == DeliveryStatus.DELIVERED) {
                deliveredAt = event.getEventTime();
            }
        }
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public AppUser getCourier() { return courier; }
    /** Get the courier with UserDirectory.require(id) first: it checks they exist and are not blocked. */
    public void assignCourier(AppUser courier) { this.courier = courier; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getPublicTrackingToken() { return publicTrackingToken; }
    public DeliveryType getDeliveryType() { return deliveryType; }
    public void setDeliveryType(DeliveryType deliveryType) { this.deliveryType = deliveryType; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(LocalDate estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public List<DeliveryAddress> getAddresses() { return addresses; }
    public List<Parcel> getParcels() { return parcels; }
    public List<TrackingEvent> getTrackingEvents() { return trackingEvents; }
}
