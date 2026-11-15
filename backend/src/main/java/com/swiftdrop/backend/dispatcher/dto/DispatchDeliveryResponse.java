package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One delivery in the admin's cross-customer lists. Unlike the customer's own view it says who booked it
 * (a customer or a merchant) and which courier carries it, and leaves out the public tracking token.
 */
public record DispatchDeliveryResponse(
        Long id,
        String trackingNumber,
        DeliveryType deliveryType,
        DeliveryStatus status,
        DeliverySource source,
        BigDecimal estimatedFee,
        String specialInstructions,
        Instant createdAt,
        Instant deliveredAt,
        Address pickup,
        Address destination,
        List<ParcelItem> parcels,
        List<Event> trackingEvents,
        AssignedCourier courier) {

    public enum DeliverySource { CUSTOMER, MERCHANT }

    public record Address(String contactName, String contactPhone, String street, String city, String district,
                          String postalCode) {
        static Address from(DeliveryAddress a) {
            return new Address(a.getContactName(), a.getContactPhone(), a.getStreet(), a.getCity(), a.getDistrict(),
                    a.getPostalCode());
        }
    }

    public record ParcelItem(Long id, String description, BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm,
                             BigDecimal heightCm, BigDecimal declaredValue) {
        static ParcelItem from(Parcel p) {
            return new ParcelItem(p.getId(), p.getDescription(), p.getWeightKg(), p.getLengthCm(), p.getWidthCm(),
                    p.getHeightCm(), p.getDeclaredValue());
        }
    }

    public record Event(DeliveryStatus status, String description, String locationName, Instant eventTime) {
        static Event from(TrackingEvent e) {
            return new Event(e.getStatus(), e.getDescription(), e.getLocationName(), e.getEventTime());
        }
    }

    /**
     * The courier on the delivery. {@code courierId} is their courier profile id (what assignments use); it is null
     * for a courier set on the delivery before profiles existed.
     */
    public record AssignedCourier(Long courierId, String name) { }

    /** Reads the lazy child collections: call inside a transaction. */
    public static DispatchDeliveryResponse from(Delivery d, AssignedCourier courier) {
        return new DispatchDeliveryResponse(
                d.getId(),
                d.getTrackingNumber(),
                d.getDeliveryType(),
                d.getStatus(),
                d.getMerchantId() != null ? DeliverySource.MERCHANT : DeliverySource.CUSTOMER,
                d.getEstimatedFee(),
                d.getSpecialInstructions(),
                d.getCreatedAt(),
                d.getDeliveredAt(),
                d.getPickupAddress().map(Address::from).orElse(null),
                d.getDestinationAddress().map(Address::from).orElse(null),
                d.getParcels().stream().map(ParcelItem::from).toList(),
                d.getTrackingEvents().stream().map(Event::from).toList(),
                courier);
    }
}
