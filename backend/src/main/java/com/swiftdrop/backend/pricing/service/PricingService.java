package com.swiftdrop.backend.pricing.service;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.pricing.Tariff;
import com.swiftdrop.backend.pricing.TariffSource;
import com.swiftdrop.backend.pricing.dto.PricingRatesRequest;
import com.swiftdrop.backend.pricing.dto.PublishedPricingResponse;
import com.swiftdrop.backend.pricing.entity.PricingVersion;
import com.swiftdrop.backend.pricing.repository.PricingVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The tariff: the newest published version, or, while none is published, the server's settings
 * ({@code swiftdrop.customer.pricing.*}, defaults in {@link Tariff#defaults()}). Publishing adds a version; it never
 * changes old ones, and bookings keep the fee they stored, so a parcel already booked is never repriced.
 */
@Service
public class PricingService implements TariffSource {

    private final PricingVersionRepository versions;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final Tariff settings;

    PricingService(PricingVersionRepository versions, UserDirectory userDirectory, Clock clock,
                   @Value("${swiftdrop.customer.pricing.distance-base:250.00}") BigDecimal distanceBase,
                   @Value("${swiftdrop.customer.pricing.included-km:5.00}") BigDecimal includedKm,
                   @Value("${swiftdrop.customer.pricing.per-km:6.00}") BigDecimal perKm,
                   @Value("${swiftdrop.customer.pricing.same-district-base:350.00}") BigDecimal sameDistrictBase,
                   @Value("${swiftdrop.customer.pricing.other-district-base:650.00}") BigDecimal otherDistrictBase,
                   @Value("${swiftdrop.customer.pricing.additional-kg:120.00}") BigDecimal additionalKg,
                   @Value("${swiftdrop.customer.pricing.express-surcharge:350.00}") BigDecimal expressSurcharge,
                   @Value("${swiftdrop.customer.pricing.same-day-surcharge:650.00}") BigDecimal sameDaySurcharge) {
        this.versions = versions;
        this.userDirectory = userDirectory;
        this.clock = clock;
        this.settings = new Tariff(0, null, money(distanceBase), money(includedKm), money(perKm),
                money(sameDistrictBase), money(otherDistrictBase), money(additionalKg),
                money(expressSurcharge), money(sameDaySurcharge));
    }

    @Override
    @Transactional(readOnly = true)
    public Tariff current() {
        return versions.findTopByOrderByVersionDesc().map(PricingVersion::toTariff).orElse(settings);
    }

    /** What the Pricing page shows: the current tariff and who published it. */
    @Transactional(readOnly = true)
    public PublishedPricingResponse published() {
        Optional<PricingVersion> newest = versions.findTopByOrderByVersionDesc();
        return newest.map(v -> PublishedPricingResponse.of(v.toTariff(), nameOf(v.getPublishedBy())))
                .orElseGet(() -> PublishedPricingResponse.of(settings, null));
    }

    /**
     * Publishes the rates as the next version and returns it. Two admins publishing at the same moment would pick
     * the same number; the UNIQUE version column refuses the second (409, publish again).
     */
    @Transactional
    public PublishedPricingResponse publish(PricingRatesRequest request, Jwt token) {
        AppUser admin = userDirectory.caller(token);
        int next = versions.findTopByOrderByVersionDesc().map(PricingVersion::getVersion).orElse(0) + 1;
        PricingVersion version = versions.saveAndFlush(new PricingVersion(next,
                money(request.distanceBase()), money(request.includedKm()), money(request.perKm()),
                money(request.sameDistrictBase()), money(request.otherDistrictBase()), money(request.additionalKg()),
                money(request.expressSurcharge()), money(request.sameDaySurcharge()),
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS), admin));
        return PublishedPricingResponse.of(version.toTariff(), nameOf(admin));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String nameOf(AppUser person) {
        return person.getName().filter(name -> !name.isBlank()).orElse("Unknown person");
    }
}
