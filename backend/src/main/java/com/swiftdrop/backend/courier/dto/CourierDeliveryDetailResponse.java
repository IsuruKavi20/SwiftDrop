package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;

import java.time.Instant;
import java.util.List;

/**
 * Full details of a delivery for the Courier Delivery Detail view.
 */
public record CourierDeliveryDetailResponse(
        Long id,
        String trackingNumber,
        DeliveryType deliveryType,
        DeliveryStatus status,
        String specialInstructions,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        String city,
        String postalCode,
        String pickupContactName,
        String pickupContactPhone,
        String pickupStreetAddress,
        String pickupCity,
        String pickupPostalCode,
        Instant assignedAt,
        Instant deliveredAt,
        List<CourierParcelResponse> parcels,
        List<CourierTrackingEventResponse> statusHistory,
        CourierDeliveryProofResponse proof,
        CourierDeliveryFailureResponse latestFailure,
        List<CourierDeliveryFailureResponse> failureHistory
) {

    public static CourierDeliveryDetailResponse from(Delivery delivery,
                                                    DeliveryProof proof,
                                                    List<DeliveryFailure> failures) {
        DeliveryAddress dest = delivery.getDestinationAddress().orElse(null);
        DeliveryAddress pickup = delivery.getPickupAddress().orElse(null);

        List<CourierParcelResponse> parcelResponses = delivery.getParcels().stream()
                .map(CourierParcelResponse::from)
                .toList();

        List<CourierTrackingEventResponse> history = delivery.getTrackingEvents().stream()
                .sorted(java.util.Comparator.comparing(com.swiftdrop.backend.customer.entity.TrackingEvent::getEventTime).reversed())
                .map(CourierTrackingEventResponse::from)
                .toList();

        List<CourierDeliveryFailureResponse> failureResponses = failures != null
                ? failures.stream().map(CourierDeliveryFailureResponse::from).toList()
                : List.of();

        CourierDeliveryFailureResponse latest = !failureResponses.isEmpty() ? failureResponses.get(0) : null;

        return new CourierDeliveryDetailResponse(
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getDeliveryType(),
                delivery.getStatus(),
                delivery.getSpecialInstructions(),
                dest != null ? dest.getContactName() : null,
                dest != null ? dest.getContactPhone() : null,
                dest != null ? dest.getStreet() : null,
                dest != null ? dest.getCity() : null,
                dest != null ? dest.getPostalCode() : null,
                pickup != null ? pickup.getContactName() : null,
                pickup != null ? pickup.getContactPhone() : null,
                pickup != null ? pickup.getStreet() : null,
                pickup != null ? pickup.getCity() : null,
                pickup != null ? pickup.getPostalCode() : null,
                delivery.getCreatedAt(),
                delivery.getDeliveredAt(),
                parcelResponses,
                history,
                CourierDeliveryProofResponse.from(proof),
                latest,
                failureResponses
        );
    }
}
