package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** The logged-in customer: pass the "sub" claim of their access token. */
    Optional<Customer> findByAuthUserId(UUID authUserId);

    boolean existsByAuthUserId(UUID authUserId);

    boolean existsByEmailIgnoreCase(String email);
}
