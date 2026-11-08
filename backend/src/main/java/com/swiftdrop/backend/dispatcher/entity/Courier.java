package com.swiftdrop.backend.dispatcher.entity;

import com.swiftdrop.backend.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Dispatcher's operational profile for a courier. Names, account status and roles come from AppUser.
 * Multiple deliveries may belong to one courier; workload is calculated from active assignments.
 */
@Entity
@Table(name = "courier")
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "courier_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_user_id", nullable = false, unique = true, updatable = false)
    private AppUser user;

    @NotBlank
    @Size(max = 20)
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Size(max = 30)
    @Column(name = "vehicle_type", length = 30)
    private String vehicleType;

    @Size(max = 30)
    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierAvailability availability = CourierAvailability.OFFLINE;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Courier() {
        // for JPA
    }

    /** Resolve the user through UserDirectory and validate the COURIER role in the service. */
    public Courier(AppUser user, String phoneNumber) {
        this.user = Objects.requireNonNull(user, "user");
        this.phoneNumber = phoneNumber;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public CourierAvailability getAvailability() { return availability; }
    public void setAvailability(CourierAvailability availability) {
        this.availability = Objects.requireNonNull(availability, "availability");
    }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
