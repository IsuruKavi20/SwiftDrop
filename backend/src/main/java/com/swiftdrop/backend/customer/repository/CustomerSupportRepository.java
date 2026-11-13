package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerSupportRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<SupportTicket> findByIdAndCustomerId(Long id, Long customerId);
}
