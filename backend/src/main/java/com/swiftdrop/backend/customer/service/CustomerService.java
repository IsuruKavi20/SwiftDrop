package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CustomerAddressRequest;
import com.swiftdrop.backend.customer.dto.CustomerAddressResponse;
import com.swiftdrop.backend.customer.dto.CustomerResponse;
import com.swiftdrop.backend.customer.dto.RegisterCustomerRequest;
import com.swiftdrop.backend.customer.dto.UpdateCustomerProfileRequest;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.CustomerAddress;
import com.swiftdrop.backend.customer.exception.ConflictException;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.repository.CustomerAddressRepository;
import com.swiftdrop.backend.customer.repository.CustomerRepository;
import com.swiftdrop.backend.identity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Customer registration, profile and saved addresses. {@code authUserId} is always the "sub" claim of the
 * caller's access token, so a customer can only ever read or change their own data.
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    public CustomerService(CustomerRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    // ---- Registration and profile ----

    /** @param user the caller, from {@code UserDirectory.caller(token)} */
    public CustomerResponse register(AppUser user, RegisterCustomerRequest request) {
        if (customers.existsByUserId(user.getId())) {
            throw new ConflictException("You are already registered as a customer");
        }
        Customer customer = new Customer(user, request.firstName().trim(), request.lastName().trim(),
                request.email().trim(), request.phoneNumber().trim());
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(UUID authUserId) {
        return CustomerResponse.from(requireCustomer(authUserId));
    }

    public CustomerResponse updateProfile(UUID authUserId, UpdateCustomerProfileRequest request) {
        Customer customer = requireCustomer(authUserId);
        customer.setPhoneNumber(request.phoneNumber().trim());
        customer.setDetails(
                keepIfBlank(request.firstName(), customer.getFirstName()),
                keepIfBlank(request.lastName(), customer.getLastName()),
                keepIfBlank(request.email(), customer.getEmail()));
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    /** The caller's customer record; 404 if they have not registered as a customer yet. Used by the other services. */
    @Transactional(readOnly = true)
    public Customer requireCustomer(UUID authUserId) {
        return customers.findByUserId(authUserId)
                .orElseThrow(() -> new NotFoundException("Customer profile not found. Please complete registration first."));
    }

    // ---- Saved addresses ----

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listAddresses(UUID authUserId) {
        Customer customer = requireCustomer(authUserId);
        return addresses.findByCustomerIdOrderByDefaultAddressDescLabelAsc(customer.getId()).stream()
                .map(CustomerAddressResponse::from).toList();
    }

    /** The first saved address becomes the default automatically; a new default replaces the old one. */
    public CustomerAddressResponse addAddress(UUID authUserId, CustomerAddressRequest request) {
        Customer customer = requireCustomer(authUserId);
        boolean makeDefault = request.makeDefault()
                || addresses.findByCustomerIdOrderByDefaultAddressDescLabelAsc(customer.getId()).isEmpty();
        if (makeDefault) {
            clearDefault(customer.getId());
        }
        CustomerAddress address = new CustomerAddress(request.label().trim(), request.street().trim(),
                request.city().trim(), request.district().trim(), blankToNull(request.postalCode()));
        address.setDefaultAddress(makeDefault);
        customer.addAddress(address);
        return CustomerAddressResponse.from(addresses.save(address));
    }

    public CustomerAddressResponse updateAddress(UUID authUserId, Long addressId, CustomerAddressRequest request) {
        CustomerAddress address = requireAddress(authUserId, addressId);
        if (request.makeDefault() && !address.isDefaultAddress()) {
            clearDefault(address.getCustomer().getId());
        }
        address.setLabel(request.label().trim());
        address.setStreet(request.street().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setPostalCode(blankToNull(request.postalCode()));
        address.setDefaultAddress(request.makeDefault());
        return CustomerAddressResponse.from(address);
    }

    /** Past deliveries keep their own copy of the address, so deleting it is always safe. */
    public void deleteAddress(UUID authUserId, Long addressId) {
        CustomerAddress address = requireAddress(authUserId, addressId);
        address.getCustomer().removeAddress(address);
    }

    private CustomerAddress requireAddress(UUID authUserId, Long addressId) {
        Customer customer = requireCustomer(authUserId);
        return addresses.findByIdAndCustomerId(addressId, customer.getId())
                .orElseThrow(() -> new NotFoundException("Address not found"));
    }

    private void clearDefault(Long customerId) {
        addresses.findByCustomerIdAndDefaultAddressTrue(customerId).ifPresent(current -> current.setDefaultAddress(false));
    }

    private static String keepIfBlank(String value, String current) {
        return value == null || value.isBlank() ? current : value.trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
