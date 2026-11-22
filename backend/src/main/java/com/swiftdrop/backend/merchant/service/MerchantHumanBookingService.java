package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.dto.ParcelRequest;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.shared.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class MerchantHumanBookingService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final MerchantRepository merchants;
    private final MerchantService profiles;
    private final MerchantDeliveryRepository deliveries;
    private final MerchantDeliveryService booking;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MerchantHumanBookingService(MerchantRepository merchants, MerchantService profiles,
                                       MerchantDeliveryRepository deliveries, MerchantDeliveryService booking,
                                       JdbcTemplate jdbc, ObjectMapper json) {
        this.merchants = merchants;
        this.profiles = profiles;
        this.deliveries = deliveries;
        this.booking = booking;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public Result book(AppUser owner, String key, CreateMerchantDeliveryRequest request) {
        if (key == null) {
            return new Result(booking.createDelivery(owner, request), false);
        }
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 128 letters, digits, dots, underscores, colons or hyphens");
        }

        // Lock the business even when this key has no row yet. On first use, serialize keyed profile creation too.
        var merchant = merchants.lockByOwnerId(owner.getId()).orElseGet(() -> {
            jdbc.queryForObject("SELECT id FROM app_users WHERE id = ? FOR UPDATE", java.util.UUID.class, owner.getId());
            profiles.profileOf(owner);
            return merchants.lockByOwnerId(owner.getId()).orElseThrow();
        });
        String hash = fingerprint(request);
        var existing = jdbc.query("""
                SELECT delivery_id, request_hash FROM merchant_human_booking
                WHERE merchant_id = ? AND idempotency_key = ?
                """, (rs, row) -> new RecordedBooking(rs.getLong(1), rs.getString(2)), merchant.getId(), key);
        if (!existing.isEmpty()) {
            var original = existing.getFirst();
            if (!original.hash().equals(hash)) {
                throw new ConflictException("Idempotency-Key was already used for a different shipment");
            }
            var delivery = deliveries.findByIdAndMerchantId(original.deliveryId(), merchant.getId()).orElseThrow();
            return new Result(MerchantDeliveryDetailResponse.from(delivery), true);
        }

        var delivery = booking.bookDelivery(merchant, owner, request);
        jdbc.update("""
                INSERT INTO merchant_human_booking (merchant_id, idempotency_key, delivery_id, request_hash)
                VALUES (?, ?, ?, ?)
                """, merchant.getId(), key, delivery.getId(), hash);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                booking.publishLatestStatus(delivery);
            }
        });
        return new Result(MerchantDeliveryDetailResponse.from(delivery), false);
    }

    private String fingerprint(CreateMerchantDeliveryRequest request) {
        var normalized = new CreateMerchantDeliveryRequest(request.deliveryType(), normalize(request.pickup()),
                normalize(request.destination()), request.parcels().stream().map(MerchantHumanBookingService::normalize).toList(),
                optionalText(request.specialInstructions()), request.recipientEmail());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(normalized)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static DeliveryAddressRequest normalize(DeliveryAddressRequest address) {
        return new DeliveryAddressRequest(address.contactName().trim(), address.contactPhone().trim(), address.street().trim(),
                address.city().trim(), address.district().trim(), optionalText(address.postalCode()));
    }

    private static ParcelRequest normalize(ParcelRequest parcel) {
        BigDecimal value = parcel.declaredValue();
        return new ParcelRequest(parcel.description().trim(), parcel.weightKg().stripTrailingZeros(),
                parcel.lengthCm().stripTrailingZeros(), parcel.widthCm().stripTrailingZeros(),
                parcel.heightCm().stripTrailingZeros(), value == null ? null : value.stripTrailingZeros());
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RecordedBooking(long deliveryId, String hash) {}

    public record Result(MerchantDeliveryDetailResponse delivery, boolean replayed) {}
}
