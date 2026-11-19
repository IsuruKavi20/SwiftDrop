package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.entity.MerchantApiBooking;
import com.swiftdrop.backend.merchant.repository.MerchantApiBookingRepository;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.merchant.security.MerchantMachineTokenContract.BookingIdentity;
import com.swiftdrop.backend.shared.error.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class MerchantIntegrationBookingService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final MerchantRepository merchants;
    private final MerchantDeliveryRepository deliveries;
    private final MerchantApiBookingRepository audit;
    private final MerchantDeliveryService booking;
    private final ObjectMapper json;
    private final Clock clock;

    public MerchantIntegrationBookingService(MerchantRepository merchants, MerchantDeliveryRepository deliveries,
                                             MerchantApiBookingRepository audit, MerchantDeliveryService booking,
                                             ObjectMapper json, Clock clock) {
        this.merchants = merchants;
        this.deliveries = deliveries;
        this.audit = audit;
        this.booking = booking;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Result book(BookingIdentity caller, String key, CreateMerchantDeliveryRequest request) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1–128 letters, digits, dots, underscores, colons or hyphens");
        }

        // Serialize retries before looking up the key, including when no audit row exists yet.
        var merchant = merchants.lockByOwnerId(caller.ownerId())
                .filter(profile -> profile.getOwner().isActive())
                .orElseThrow(() -> new AccessDeniedException("Merchant business is unavailable"));
        String hash = fingerprint(request);
        var existing = audit.findByMerchantIdAndClientIdAndIdempotencyKey(merchant.getId(), caller.clientId(), key);
        if (existing.isPresent()) {
            var attempt = existing.get();
            if (!attempt.matchesRequest(hash)) {
                throw new ConflictException("Idempotency-Key was already used for a different shipment");
            }
            var delivery = deliveries.findByIdAndMerchantId(attempt.getDeliveryId(), merchant.getId())
                    .orElseThrow(() -> new IllegalStateException("API booking has no owned delivery"));
            return new Result(MerchantDeliveryDetailResponse.from(delivery), true);
        }

        var delivery = booking.bookDelivery(merchant, null, request);
        audit.saveAndFlush(new MerchantApiBooking(delivery.getId(), merchant.getId(), caller.clientId(), key, hash,
                clock.instant()));
        booking.publishLatestStatus(delivery);
        return new Result(MerchantDeliveryDetailResponse.from(delivery), false);
    }

    private String fingerprint(CreateMerchantDeliveryRequest request) {
        try {
            byte[] bytes = json.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record Result(MerchantDeliveryDetailResponse delivery, boolean replayed) {}
}
