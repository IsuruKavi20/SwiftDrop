package com.swiftdrop.backend.merchant.dto;

import com.swiftdrop.backend.customer.entity.DeliveryAddress;

/** Full pickup/destination details, including contact info, for a merchant delivery. */
public record DeliveryAddressResponse(
        String contactName,
        String contactPhone,
        String street,
        String city,
        String district,
        String postalCode) {

    public static DeliveryAddressResponse from(DeliveryAddress address) {
        return new DeliveryAddressResponse(address.getContactName(), address.getContactPhone(), address.getStreet(),
                address.getCity(), address.getDistrict(), address.getPostalCode());
    }
}
