package com.swiftdrop.backend.merchant.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class MerchantMachineBookingAccess {
    private final ObjectProvider<MerchantMachineTokenContract> contracts;

    public MerchantMachineBookingAccess(ObjectProvider<MerchantMachineTokenContract> contracts) {
        this.contracts = contracts;
    }

    public MerchantMachineTokenContract.BookingIdentity requireBooking(Jwt token) {
        var contract = contracts.getIfUnique();
        if (token == null || contract == null || !contract.isMachine(token)) {
            throw new AccessDeniedException("Merchant machine booking is not authorized");
        }
        return contract.authorizeBooking(token)
                .orElseThrow(() -> new AccessDeniedException("Merchant machine booking is not authorized"));
    }

    public boolean isMachine(Jwt token) {
        var contract = contracts.getIfUnique();
        return token != null && contract != null && contract.isMachine(token);
    }
}
