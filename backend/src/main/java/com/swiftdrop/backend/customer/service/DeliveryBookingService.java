package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.customer.dto.DeliveryDetailsResponse;
import com.swiftdrop.backend.customer.dto.ParcelRequest;
import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.exception.ConflictException;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Delivery booking: create a booking (status PENDING), let the customer review it, then confirm it.
 * The backend sets the tracking number, public token, status and estimated date; the client never can.
 */
@Service
@Transactional
public class DeliveryBookingService {

    private static final int MAX_TRACKING_NUMBER_ATTEMPTS = 5;

    private final CustomerService customerService;
    private final CustomerDeliveryRepository deliveries;
    private final TrackingCodeGenerator codes;
    private final Clock clock;

    public DeliveryBookingService(CustomerService customerService, CustomerDeliveryRepository deliveries,
                                  TrackingCodeGenerator codes, Clock clock) {
        this.customerService = customerService;
        this.deliveries = deliveries;
        this.codes = codes;
        this.clock = clock;
    }

    public DeliveryDetailsResponse createDelivery(UUID authUserId, CreateDeliveryRequest request) {
        Customer customer = customerService.requireCustomer(authUserId);

        Delivery delivery = new Delivery(customer, uniqueTrackingNumber(), codes.newPublicTrackingToken(), request.deliveryType());
        delivery.setSpecialInstructions(CustomerService.blankToNull(request.specialInstructions()));
        delivery.setEstimatedDeliveryDate(estimatedDeliveryDate(request.deliveryType()));
        delivery.setAddress(toDeliveryAddress(AddressType.PICKUP, request.pickup()));
        delivery.setAddress(toDeliveryAddress(AddressType.DESTINATION, request.destination()));
        request.parcels().forEach(parcel -> delivery.addParcel(toParcel(parcel)));
        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.PENDING, "Booking created, awaiting confirmation",
                null, clock.instant(), authUserId));

        return DeliveryDetailsResponse.from(deliveries.save(delivery));
    }

    /** Review and Confirmation: only the booking's own customer, and only while it is still PENDING. */
    public DeliveryDetailsResponse confirmDelivery(UUID authUserId, Long deliveryId) {
        Customer customer = customerService.requireCustomer(authUserId);
        Delivery delivery = deliveries.findByIdAndCustomerId(deliveryId, customer.getId())
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new ConflictException("Only a pending booking can be confirmed; this one is " + delivery.getStatus());
        }
        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.CONFIRMED, "Booking confirmed", null,
                clock.instant(), authUserId));
        return DeliveryDetailsResponse.from(deliveries.saveAndFlush(delivery));
    }

    /** Tracking numbers are random; on the rare clash with an existing one, draw again. */
    private String uniqueTrackingNumber() {
        for (int attempt = 0; attempt < MAX_TRACKING_NUMBER_ATTEMPTS; attempt++) {
            String candidate = codes.newTrackingNumber();
            if (!deliveries.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking number");
    }

    /** Same day: today; express: next day; standard: three days. */
    LocalDate estimatedDeliveryDate(DeliveryType type) {
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
                CustomerService.blankToNull(request.postalCode()));
    }

    private static Parcel toParcel(ParcelRequest request) {
        Parcel parcel = new Parcel(request.description().trim(), request.weightKg(), request.lengthCm(),
                request.widthCm(), request.heightCm());
        parcel.setDeclaredValue(request.declaredValue());
        return parcel;
    }
}
