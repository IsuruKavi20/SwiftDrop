package com.swiftdrop.backend.dispatcher.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A delivery zone: a named group of Sri Lankan districts. Each district is in one zone at most (UNIQUE district
 * column). Zones don't change prices or bookings yet.
 */
@Entity
@Table(name = "delivery_zone")
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 4)
    private String code;

    @Column(nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "delivery_zone_district", joinColumns = @JoinColumn(name = "zone_id"))
    @Column(name = "district", nullable = false, length = 30)
    private Set<String> districts = new HashSet<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryZone() {
        // for JPA
    }

    public DeliveryZone(String name, String code, Set<String> districts, boolean active) {
        update(name, code, districts, active);
    }

    /** The service checks the values (code format, known districts, no district in two zones) first. */
    public void update(String name, String code, Set<String> districts, boolean active) {
        this.name = name;
        this.code = code;
        this.active = active;
        // Change the managed set in place, so Hibernate only deletes and inserts what changed
        this.districts.retainAll(districts);
        this.districts.addAll(districts);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public boolean isActive() { return active; }
    public Set<String> getDistricts() { return Set.copyOf(districts); }
    public long getVersion() { return version; }
}
