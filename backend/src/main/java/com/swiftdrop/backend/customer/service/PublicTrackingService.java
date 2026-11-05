package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.TrackingResponse;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Tracking, no login needed. Always answers with {@link TrackingResponse}, which holds no private data
 * (no names, phone numbers, street addresses or parcel contents), whoever is asking.
 */
@Service
@Transactional(readOnly = true)
public class PublicTrackingService {

    private static final String NOT_FOUND = "No delivery found for this tracking number or link";

    private final CustomerDeliveryRepository deliveries;

    public PublicTrackingService(CustomerDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    public TrackingResponse trackByTrackingNumber(String trackingNumber) {
        return deliveries.findByTrackingNumber(CustomerDeliveryService.normalize(trackingNumber))
                .map(TrackingResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** For shared tracking links: the token is random and unguessable. */
    public TrackingResponse trackByPublicToken(String publicTrackingToken) {
        if (publicTrackingToken == null || publicTrackingToken.isBlank()) {
            throw new NotFoundException(NOT_FOUND);
        }
        return deliveries.findByPublicTrackingToken(publicTrackingToken.trim())
                .map(TrackingResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }
}
