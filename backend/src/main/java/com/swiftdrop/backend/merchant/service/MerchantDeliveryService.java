package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.customer.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.customer.dto.PageResponse;
import com.swiftdrop.backend.customer.dto.ParcelRequest;
import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.service.TrackingCodeGenerator;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDashboardResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliverySummaryResponse;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository.StatusCount;
import com.swiftdrop.backend.notification.service.NotificationPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles merchant order operations: booking deliveries, retrieving dashboard metrics,
 * paginating deliveries, and viewing detailed tracking information.
 */
@Service
@Transactional
public class MerchantDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MerchantDeliveryService.class);
    private static final int MAX_TRACKING_ATTEMPTS = 5;
    private static final int MAX_PAGE_SIZE = 50;

    private final MerchantService merchantService;
    private final MerchantDeliveryRepository deliveries;
    private final TrackingCodeGenerator codes;
    private final NotificationPublisherService notifications;
    private final Clock clock;

    public MerchantDeliveryService(MerchantService merchantService,
                                   MerchantDeliveryRepository deliveries,
                                   TrackingCodeGenerator codes,
                                   NotificationPublisherService notifications,
                                   Clock clock) {
        this.merchantService = merchantService;
        this.deliveries = deliveries;
        this.codes = codes;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Books a new delivery belonging to the authenticated merchant.
     */
    public MerchantDeliveryDetailResponse createDelivery(AppUser owner, CreateMerchantDeliveryRequest request) {
        Merchant merchant = merchantService.profileOf(owner);

        Delivery delivery = new Delivery(merchant, uniqueTrackingNumber(), codes.newPublicTrackingToken(), request.deliveryType());
        delivery.setSpecialInstructions(blankToNull(request.specialInstructions()));
        delivery.setEstimatedDeliveryDate(estimatedDeliveryDate(request.deliveryType()));
        delivery.setAddress(toDeliveryAddress(AddressType.PICKUP, request.pickup()));
        delivery.setAddress(toDeliveryAddress(AddressType.DESTINATION, request.destination()));
        request.parcels().forEach(parcel -> delivery.addParcel(toParcel(parcel)));

        TrackingEvent initialEvent = new TrackingEvent(DeliveryStatus.PENDING, "Delivery booked by merchant", null, clock.instant(), owner);
        delivery.addTrackingEvent(initialEvent);

        Delivery saved = deliveries.save(delivery);

        try {
            notifications.publishDeliveryStatusChanged(saved, initialEvent);
        } catch (Exception ex) {
            log.warn("Failed to publish notification event for merchant delivery {}: {}", saved.getTrackingNumber(), ex.getMessage());
        }

        return MerchantDeliveryDetailResponse.from(saved);
    }

    /**
     * Retrieves paginated deliveries for the authenticated merchant with an optional status filter.
     */
    @Transactional(readOnly = true)
    public PageResponse<MerchantDeliverySummaryResponse> getDeliveries(AppUser owner, DeliveryStatus status, int page, int size) {
        Merchant merchant = merchantService.profileOf(owner);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));

        Page<Delivery> results = status != null
                ? deliveries.findByMerchantIdAndStatusOrderByCreatedAtDescIdDesc(merchant.getId(), status, pageRequest)
                : deliveries.findByMerchantIdOrderByCreatedAtDescIdDesc(merchant.getId(), pageRequest);

        return PageResponse.from(results, MerchantDeliverySummaryResponse::from);
    }

    /**
     * Fetches detailed information for a specific delivery owned by the authenticated merchant.
     */
    @Transactional(readOnly = true)
    public MerchantDeliveryDetailResponse getDeliveryDetail(AppUser owner, Long deliveryId) {
        Merchant merchant = merchantService.profileOf(owner);
        return deliveries.findByIdAndMerchantId(deliveryId, merchant.getId())
                .map(MerchantDeliveryDetailResponse::from)
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
    }

    /**
     * Aggregates dashboard metrics (counts by status, totals) and recent orders for the merchant.
     */
    @Transactional(readOnly = true)
    public MerchantDashboardResponse getDashboard(AppUser owner) {
        Merchant merchant = merchantService.profileOf(owner);
        List<StatusCount> counts = deliveries.countByStatus(merchant.getId());
        List<MerchantDeliverySummaryResponse> recent = deliveries
                .findByMerchantIdOrderByCreatedAtDescIdDesc(merchant.getId(), PageRequest.of(0, 5))
                .map(MerchantDeliverySummaryResponse::from)
                .getContent();

        return MerchantDashboardResponse.of(merchant, counts, recent);
    }

    private String uniqueTrackingNumber() {
        for (int attempt = 0; attempt < MAX_TRACKING_ATTEMPTS; attempt++) {
            String candidate = codes.newTrackingNumber();
            if (!deliveries.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking number");
    }

    private LocalDate estimatedDeliveryDate(DeliveryType type) {
        LocalDate today = LocalDate.now(clock);
        return switch (type) {
            case SAME_DAY -> today;
            case EXPRESS -> today.plusDays(1);
            case STANDARD -> today.plusDays(3);
        };
    }

    private static DeliveryAddress toDeliveryAddress(AddressType type, DeliveryAddressRequest request) {
        return new DeliveryAddress(type, request.contactName().trim(), request.contactPhone().trim(),
                request.street().trim(), request.city().trim(), request.district().trim(),
                blankToNull(request.postalCode()));
    }

    private static Parcel toParcel(ParcelRequest request) {
        Parcel parcel = new Parcel(request.description().trim(), request.weightKg(), request.lengthCm(),
                request.widthCm(), request.heightCm());
        parcel.setDeclaredValue(request.declaredValue());
        return parcel;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
