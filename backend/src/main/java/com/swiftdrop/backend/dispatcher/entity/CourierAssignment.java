package com.swiftdrop.backend.dispatcher.entity;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;

/**
 * One assignment in a delivery's history. Reassignment closes this row and creates another.
 * Services must update Delivery.assignCourier in the same transaction and lock the courier
 * while checking availability/workload. These entities do not implement authorization or dispatch.
 */
@Entity
@Table(name = "courier_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_delivery_active",
                columnNames = {"delivery_id", "active"}),
        indexes = @Index(name = "idx_assignment_courier_active", columnList = "courier_id, active"))
@Check(constraints = "(active is not null and active = true and ended_at is null and ended_by is null)"
        + " or (active is null and ended_at is not null and ended_by is not null and ended_at >= assigned_at)")
public class CourierAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false, updatable = false)
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false, updatable = false)
    private AppUser assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    /**
     * TRUE for the active assignment; NULL for history. The unique (delivery_id, active)
     * constraint permits many historical rows but at most one active row in PostgreSQL/H2.
     * Do not store FALSE: the table check constraint rejects it.
     */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by")
    private AppUser endedBy;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Size(max = 500)
    @Column(name = "end_reason", length = 500)
    private String endReason;

    @Version
    @Column(nullable = false)
    private long version;

    protected CourierAssignment() {
        // for JPA
    }

    public CourierAssignment(Delivery delivery, Courier courier, AppUser assignedBy, Instant assignedAt) {
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.courier = Objects.requireNonNull(courier, "courier");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
    }

    /** Close and flush this assignment before inserting its replacement in the same transaction. */
    public void endAssignment(AppUser endedBy, Instant endedAt, String reason) {
        if (!isActive()) {
            throw new IllegalStateException("Assignment has already ended");
        }
        Objects.requireNonNull(endedBy, "endedBy");
        Objects.requireNonNull(endedAt, "endedAt");
        if (endedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("Assignment cannot end before it was assigned");
        }
        this.endedBy = endedBy;
        this.endedAt = endedAt;
        this.endReason = reason;
        this.active = null;
    }

    public Long getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    public Courier getCourier() { return courier; }
    public AppUser getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public AppUser getEndedBy() { return endedBy; }
    public Instant getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }
    public long getVersion() { return version; }
}
