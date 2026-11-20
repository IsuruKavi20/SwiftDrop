package com.swiftdrop.backend.courier.entity;

import com.swiftdrop.backend.identity.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational profile for a courier personnel.
 * Authentication, credentials, and roles are managed through {@link AppUser}.
 * This profile holds operational metadata (vehicle, zone, contact) used by Courier Portal and Dispatcher.
 */
@Entity
@Table(name = "courier_profile")
public class CourierProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "courier_profile_id")
    private Long id;

    /** The associated user account from the identity/auth service. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_user_id", nullable = false, unique = true, updatable = false)
    private AppUser user;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    /** Vehicle type e.g. MOTORCYCLE, VAN, BICYCLE, CAR */
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;

    /** Operational zone e.g. "Colombo Central", "Kurunegala North" */
    @Column(name = "assigned_zone", nullable = false, length = 100)
    private String assignedZone;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourierProfile() {
        // for JPA
    }

    public CourierProfile(AppUser user, String phoneNumber, String vehicleType, String assignedZone) {
        this.user = user;
        this.phoneNumber = phoneNumber;
        this.vehicleType = vehicleType;
        this.assignedZone = assignedZone;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public UUID getUserId() {
        return user != null ? user.getId() : null;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getAssignedZone() {
        return assignedZone;
    }

    public void setAssignedZone(String assignedZone) {
        this.assignedZone = assignedZone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
