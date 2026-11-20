package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.DashboardResponse;
import com.swiftdrop.backend.customer.dto.DeliveryDetailsResponse;
import com.swiftdrop.backend.customer.dto.DeliverySummaryResponse;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.dto.TrackingResponse;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Delivery management and tracking for the logged-in customer: dashboard, My Deliveries (with search),
 * delivery details and Track Parcel. Every lookup is limited to the caller's own deliveries.
 */
@Service
@Transactional(readOnly = true)
public class CustomerDeliveryService {

    static final int RECENT_DELIVERIES = 5;
    static final int MAX_PAGE_SIZE = 50;

    private final CustomerService customerService;
    private final CustomerDeliveryRepository deliveries;

    public CustomerDeliveryService(CustomerService customerService, CustomerDeliveryRepository deliveries) {
        this.customerService = customerService;
        this.deliveries = deliveries;
    }

    public DashboardResponse getDashboard(UUID authUserId) {
        Customer customer = customerService.requireCustomer(authUserId);
        List<DeliverySummaryResponse> recent = deliveries
                .findByCustomerIdOrderByCreatedAtDesc(customer.getId(), PageRequest.of(0, RECENT_DELIVERIES))
                .map(DeliverySummaryResponse::from).getContent();
        return DashboardResponse.of(deliveries.countByStatus(customer.getId()), recent);
    }

    /**
     * My Deliveries, newest first. {@code status} and {@code search} are optional filters (null or blank = all);
     * search matches the tracking number, receiver name or destination city. Pages start at 0.
     */
    public PageResponse<DeliverySummaryResponse> getMyDeliveries(UUID authUserId, DeliveryStatus status, String search,
                                                                 int page, int size) {
        Customer customer = customerService.requireCustomer(authUserId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                deliveries.search(customer.getId(), status, CustomerService.blankToNull(search), pageRequest),
                DeliverySummaryResponse::from);
    }

    public DeliveryDetailsResponse getDeliveryDetails(UUID authUserId, Long deliveryId) {
        Customer customer = customerService.requireCustomer(authUserId);
        return deliveries.findByIdAndCustomerId(deliveryId, customer.getId())
                .map(DeliveryDetailsResponse::from)
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
    }

    /** Track Parcel for a logged-in customer: current status and the timeline of one of their own deliveries. */
    public TrackingResponse trackMyDelivery(UUID authUserId, String trackingNumber) {
        Customer customer = customerService.requireCustomer(authUserId);
        return deliveries.findByTrackingNumberAndCustomerId(normalize(trackingNumber), customer.getId())
                .map(TrackingResponse::from)
                .orElseThrow(() -> new NotFoundException("No delivery found with this tracking number"));
    }

    /** Customers type tracking numbers in any case and with stray spaces. */
    static String normalize(String trackingNumber) {
        return trackingNumber == null ? "" : trackingNumber.trim().toUpperCase(Locale.ROOT);
    }
}
