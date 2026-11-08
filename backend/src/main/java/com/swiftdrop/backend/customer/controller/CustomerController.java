package com.swiftdrop.backend.customer.controller;

import com.swiftdrop.backend.customer.dto.CustomerAddressRequest;
import com.swiftdrop.backend.customer.dto.CustomerAddressResponse;
import com.swiftdrop.backend.customer.dto.CustomerResponse;
import com.swiftdrop.backend.customer.dto.RegisterCustomerRequest;
import com.swiftdrop.backend.customer.dto.UpdateCustomerProfileRequest;
import com.swiftdrop.backend.customer.service.CustomerService;
import jakarta.validation.Valid;
import com.swiftdrop.backend.identity.service.UserDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer registration, profile and saved addresses. "me" is always the logged-in user (the token's "sub").
 * Anyone who may book deliveries ("parcel:create", given to every CUSTOMER) manages their own profile here.
 */
@RestController
@RequestMapping("/api/customers/me")
@PreAuthorize("hasAuthority('parcel:create')")
class CustomerController {

    private final CustomerService customers;
    private final UserDirectory userDirectory;

    CustomerController(CustomerService customers, UserDirectory userDirectory) {
        this.customers = customers;
        this.userDirectory = userDirectory;
    }

    /** Called once after the user signs up at the auth service: creates their customer profile. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request, @AuthenticationPrincipal Jwt user) {
        return customers.register(userDirectory.caller(user), request);
    }

    @GetMapping
    CustomerResponse getProfile(@AuthenticationPrincipal Jwt user) {
        return customers.getProfile(CurrentUser.id(user));
    }

    @PutMapping
    CustomerResponse updateProfile(@Valid @RequestBody UpdateCustomerProfileRequest request, @AuthenticationPrincipal Jwt user) {
        return customers.updateProfile(CurrentUser.id(user), request);
    }

    @GetMapping("/addresses")
    List<CustomerAddressResponse> listAddresses(@AuthenticationPrincipal Jwt user) {
        return customers.listAddresses(CurrentUser.id(user));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerAddressResponse addAddress(@Valid @RequestBody CustomerAddressRequest request, @AuthenticationPrincipal Jwt user) {
        return customers.addAddress(CurrentUser.id(user), request);
    }

    @PutMapping("/addresses/{addressId}")
    CustomerAddressResponse updateAddress(@PathVariable Long addressId, @Valid @RequestBody CustomerAddressRequest request,
                                          @AuthenticationPrincipal Jwt user) {
        return customers.updateAddress(CurrentUser.id(user), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@PathVariable Long addressId, @AuthenticationPrincipal Jwt user) {
        customers.deleteAddress(CurrentUser.id(user), addressId);
    }
}
