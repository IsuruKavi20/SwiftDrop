package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.CustomerAddress;

public record CustomerAddressResponse(
        Long id,
        String label,
        String street,
        String city,
        String district,
        String postalCode,
        boolean defaultAddress) {

    public static CustomerAddressResponse from(CustomerAddress address) {
        return new CustomerAddressResponse(address.getId(), address.getLabel(), address.getStreet(), address.getCity(),
                address.getDistrict(), address.getPostalCode(), address.isDefaultAddress());
    }
}
