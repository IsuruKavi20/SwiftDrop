package com.swiftdrop.backend.pricing.entity;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.pricing.Tariff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One published tariff. Never changed after it is saved: a new publish adds a new version. */
@Entity
@Table(name = "pricing_version")
public class PricingVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricing_version_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private int version;

    @Column(name = "same_district_base", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal sameDistrictBase;

    @Column(name = "other_district_base", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal otherDistrictBase;

    @Column(name = "additional_kg", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal additionalKg;

    @Column(name = "express_surcharge", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal expressSurcharge;

    @Column(name = "same_day_surcharge", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal sameDaySurcharge;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by", nullable = false, updatable = false)
    private AppUser publishedBy;

    protected PricingVersion() {
        // for JPA
    }

    public PricingVersion(int version, BigDecimal sameDistrictBase, BigDecimal otherDistrictBase, BigDecimal additionalKg,
                          BigDecimal expressSurcharge, BigDecimal sameDaySurcharge, Instant publishedAt, AppUser publishedBy) {
        this.version = version;
        this.sameDistrictBase = Objects.requireNonNull(sameDistrictBase);
        this.otherDistrictBase = Objects.requireNonNull(otherDistrictBase);
        this.additionalKg = Objects.requireNonNull(additionalKg);
        this.expressSurcharge = Objects.requireNonNull(expressSurcharge);
        this.sameDaySurcharge = Objects.requireNonNull(sameDaySurcharge);
        this.publishedAt = Objects.requireNonNull(publishedAt);
        this.publishedBy = Objects.requireNonNull(publishedBy);
    }

    public Tariff toTariff() {
        return new Tariff(version, publishedAt, sameDistrictBase, otherDistrictBase, additionalKg, expressSurcharge,
                sameDaySurcharge);
    }

    public Long getId() { return id; }
    public int getVersion() { return version; }
    public Instant getPublishedAt() { return publishedAt; }
    public AppUser getPublishedBy() { return publishedBy; }
}
