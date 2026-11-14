package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Known shortcut (docs/MODULES.md): notification reads this directly. To be replaced by a small API or events;
// listed here so the module guard allows only this, not the rest of the package.
@org.springframework.modulith.NamedInterface("shortcut-CustomerRepository")
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** The logged-in customer: pass the "sub" claim of their access token. */
    Optional<Customer> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
