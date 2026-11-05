package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    /** A customer's saved addresses, default first. */
    List<CustomerAddress> findByCustomerIdOrderByDefaultAddressDescLabelAsc(Long customerId);

    /** One saved address, only if it belongs to this customer (empty otherwise, so nobody reads another's address). */
    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);

    Optional<CustomerAddress> findByCustomerIdAndDefaultAddressTrue(Long customerId);
}
