package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.identity.entity.TestPeople;
import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository.StatusCount;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerModuleDtoTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static DeliveryAddressRequest address(String phone) {
        return new DeliveryAddressRequest("Nimal Perera", phone, "12 Galle Road", "Colombo", "Colombo", "00300");
    }

    private static ParcelRequest parcel(String weight) {
        return new ParcelRequest("Books", weight == null ? null : new BigDecimal(weight),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), null);
    }

    @Test
    void aCompleteBookingIsValid() {
        CreateDeliveryRequest request = new CreateDeliveryRequest(DeliveryType.STANDARD, address("0771234567"),
                address("+94719876543"), List.of(parcel("2.50")), "Call before arriving");
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void bookingValidationReachesIntoAddressesAndParcels() {
        CreateDeliveryRequest request = new CreateDeliveryRequest(null, address("12ab"), null,
                List.of(parcel("0"), parcel(null)), null);
        assertThat(invalidFields(request)).containsExactlyInAnyOrder(
                "deliveryType", "pickup.contactPhone", "destination", "parcels[0].weightKg", "parcels[1].weightKg");
    }

    @Test
    void aBookingNeedsAtLeastOneParcel() {
        CreateDeliveryRequest request = new CreateDeliveryRequest(DeliveryType.EXPRESS, address("0771234567"),
                address("0771234567"), List.of(), null);
        assertThat(invalidFields(request)).containsExactly("parcels");
    }

    @Test
    void registrationChecksThePhone() {
        assertThat(invalidFields(new RegisterCustomerRequest("077"))).containsExactly("phoneNumber");
        assertThat(invalidFields(new RegisterCustomerRequest("0771234567"))).isEmpty();
    }

    private static Delivery bookedDelivery() {
        Customer customer = new Customer(TestPeople.unsaved("Nimal Perera", "CUSTOMER"), "0771234567");
        Delivery delivery = new Delivery(customer, "SD-000001", "secret-public-token", DeliveryType.EXPRESS);
        delivery.setAddress(new DeliveryAddress(AddressType.PICKUP, "Nimal Perera", "0771234567",
                "12 Galle Road", "Colombo", "Colombo", null));
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Kamala Silva", "0719876543",
                "5 Temple Road", "Kandy", "Kandy", null));
        delivery.addParcel(new Parcel("Passport documents", new BigDecimal("0.50"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE));
        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.IN_TRANSIT, "Left Colombo hub", "Colombo",
                Instant.parse("2026-11-02T08:00:00Z"), TestPeople.unsaved("Kasun Silva", "COURIER")));
        return delivery;
    }

    @Test
    void publicTrackingShowsProgressButNoPrivateData() {
        TrackingResponse tracking = TrackingResponse.from(bookedDelivery());

        assertThat(tracking.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(tracking.fromCity()).isEqualTo("Colombo");
        assertThat(tracking.toCity()).isEqualTo("Kandy");
        assertThat(tracking.events()).singleElement().extracting(TrackingEventResponse::locationName).isEqualTo("Colombo");
        String everything = tracking.toString();
        assertThat(everything).doesNotContain("Nimal", "Kamala", "0771234567", "0719876543",
                "Galle Road", "Temple Road", "Passport", "secret-public-token");
    }

    @Test
    void detailsForTheOwnerIncludeEverything() {
        DeliveryDetailsResponse details = DeliveryDetailsResponse.from(bookedDelivery());

        assertThat(details.pickup().contactName()).isEqualTo("Nimal Perera");
        assertThat(details.destination().street()).isEqualTo("5 Temple Road");
        assertThat(details.parcels()).singleElement().extracting(ParcelResponse::description).isEqualTo("Passport documents");
        assertThat(details.publicTrackingToken()).isEqualTo("secret-public-token");
        assertThat(DeliverySummaryResponse.from(bookedDelivery()).receiverName()).isEqualTo("Kamala Silva");
    }

    @Test
    void dashboardListsEveryStatusAndCountsActiveDeliveries() {
        DashboardResponse dashboard = DashboardResponse.of(
                List.of(count(DeliveryStatus.IN_TRANSIT, 2), count(DeliveryStatus.DELIVERED, 3), count(DeliveryStatus.PENDING, 1)),
                List.of());

        assertThat(dashboard.totalDeliveries()).isEqualTo(6);
        assertThat(dashboard.activeDeliveries()).isEqualTo(2);
        assertThat(dashboard.countsByStatus()).hasSize(DeliveryStatus.values().length)
                .containsEntry(DeliveryStatus.CANCELLED, 0L).containsEntry(DeliveryStatus.DELIVERED, 3L);
    }

    private static StatusCount count(DeliveryStatus status, long total) {
        return new StatusCount() {
            public DeliveryStatus getStatus() { return status; }
            public long getTotal() { return total; }
        };
    }
}
