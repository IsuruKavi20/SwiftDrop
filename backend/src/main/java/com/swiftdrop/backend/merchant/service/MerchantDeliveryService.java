package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDashboardResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliverySummaryResponse;
import com.swiftdrop.backend.merchant.dto.ParcelRequest;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.event.MerchantDeliveryConfirmed;
import com.swiftdrop.backend.merchant.event.MerchantStatusChange;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository.StatusCount;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.pricing.DeliveryPricing;
import com.swiftdrop.backend.shared.error.ConflictException;
import com.swiftdrop.backend.shared.error.NotFoundException;
import com.swiftdrop.backend.shared.util.TrackingCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final DeliveryPricing pricing;

    public MerchantDeliveryService(MerchantService merchantService,
                                   MerchantDeliveryRepository deliveries,
                                   TrackingCodeGenerator codes,
                                   ApplicationEventPublisher events,
                                   Clock clock,
                                   DeliveryPricing pricing) {
        this.merchantService = merchantService;
        this.deliveries = deliveries;
        this.codes = codes;
        this.events = events;
        this.clock = clock;
        this.pricing = pricing;
    }

    public DeliveryPricing.Quote estimate(CreateMerchantDeliveryRequest request) {
        return pricing.estimate(pricingAddress(request.pickup()), pricingAddress(request.destination()),
                request.deliveryType().name(), request.parcels().stream().map(ParcelRequest::weightKg).toList());
    }

    /**
     * Books a new delivery belonging to the authenticated merchant.
     */
    public MerchantDeliveryDetailResponse createDelivery(AppUser owner, CreateMerchantDeliveryRequest request) {
        Delivery saved = bookDelivery(merchantService.profileOf(owner), owner, request);
        publishLatestStatus(saved);
        return MerchantDeliveryDetailResponse.from(saved);
    }

    Delivery bookDelivery(Merchant merchant, AppUser recordedBy, CreateMerchantDeliveryRequest request) {
        Delivery delivery = new Delivery(merchant.getId(), uniqueTrackingNumber(), codes.newPublicTrackingToken(), request.deliveryType());
        delivery.setSpecialInstructions(blankToNull(request.specialInstructions()));
        delivery.setRecipientEmail(request.recipientEmail());
        delivery.setEstimatedFee(estimate(request).totalFee());
        delivery.setEstimatedDeliveryDate(estimatedDeliveryDate(request.deliveryType()));
        delivery.setAddress(toDeliveryAddress(AddressType.PICKUP, request.pickup()));
        delivery.setAddress(toDeliveryAddress(AddressType.DESTINATION, request.destination()));
        request.parcels().forEach(parcel -> delivery.addParcel(toParcel(parcel)));

        String description = recordedBy == null ? "Delivery booked via Merchant API" : "Delivery booked by merchant";
        TrackingEvent initialEvent = new TrackingEvent(DeliveryStatus.PENDING, description, null, clock.instant(), recordedBy);
        delivery.addTrackingEvent(initialEvent);

        return deliveries.saveAndFlush(delivery);
    }

    public MerchantDeliveryDetailResponse confirmDelivery(AppUser owner, Long id) {
        Merchant merchant = merchantService.profileOf(owner);
        Delivery delivery = deliveries.lockMerchantDelivery(id, merchant.getId())
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new ConflictException("Only a pending booking can be confirmed; this one is " + delivery.getStatus());
        }
        TrackingEvent event = new TrackingEvent(DeliveryStatus.CONFIRMED, "Booking confirmed by merchant",
                null, clock.instant(), owner);
        delivery.addTrackingEvent(event);
        Delivery saved = deliveries.saveAndFlush(delivery);
        TrackingEvent persisted = saved.getTrackingEvents().getLast();
        events.publishEvent(new MerchantDeliveryConfirmed(saved.getId(), persisted.getId(), merchant.getId(),
                saved.getTrackingNumber(), persisted.getEventTime()));
        return MerchantDeliveryDetailResponse.from(saved);
    }

    void publishLatestStatus(Delivery saved) {
        try {
            // Merge may replace a newly added event with its persisted copy.
            TrackingEvent persisted = saved.getTrackingEvents().getLast();
            events.publishEvent(new MerchantStatusChange(saved.getId(), persisted.getId()));
        } catch (Exception ex) {
            log.warn("Failed to publish notification event for merchant delivery {}: {}", saved.getTrackingNumber(), ex.getMessage());
        }

    }

    /**
     * Retrieves paginated deliveries for the authenticated merchant with an optional status filter.
     */
    @Transactional(readOnly = true)
    public PageResponse<MerchantDeliverySummaryResponse> getDeliveries(AppUser owner, DeliveryStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Merchant merchant = merchantService.findProfile(owner).orElse(null);
        if (merchant == null) {
            return PageResponse.from(Page.<Delivery>empty(pageRequest), MerchantDeliverySummaryResponse::from);
        }

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
        return merchantService.findProfile(owner)
                .flatMap(merchant -> deliveries.findByIdAndMerchantId(deliveryId, merchant.getId()))
                .map(MerchantDeliveryDetailResponse::from)
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
    }

    /**
     * Aggregates dashboard metrics (counts by status, totals) and recent orders for the merchant.
     */
    @Transactional(readOnly = true)
    public MerchantDashboardResponse getDashboard(AppUser owner) {
        Merchant merchant = merchantService.findProfile(owner).orElse(null);
        if (merchant == null) {
            return MerchantDashboardResponse.of(new Merchant(owner), List.of(), List.of());
        }

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

    private static DeliveryPricing.Address pricingAddress(DeliveryAddressRequest request) {
        return new DeliveryPricing.Address(request.street(), request.city(), request.district(), request.postalCode());
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
