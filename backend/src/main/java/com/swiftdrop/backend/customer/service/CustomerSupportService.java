package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.AddSupportMessageRequest;
import com.swiftdrop.backend.customer.dto.CreateSupportTicketRequest;
import com.swiftdrop.backend.customer.dto.SupportTicketResponse;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.SupportMessage;
import com.swiftdrop.backend.customer.entity.SupportTicket;
import com.swiftdrop.backend.customer.entity.SupportTicketStatus;
import com.swiftdrop.backend.customer.exception.ConflictException;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.customer.repository.CustomerSupportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CustomerSupportService {
    private final CustomerService customers;
    private final CustomerDeliveryRepository deliveries;
    private final CustomerSupportRepository tickets;

    public CustomerSupportService(CustomerService customers, CustomerDeliveryRepository deliveries,
                                  CustomerSupportRepository tickets) {
        this.customers = customers;
        this.deliveries = deliveries;
        this.tickets = tickets;
    }

    public SupportTicketResponse create(UUID userId, CreateSupportTicketRequest request) {
        Customer customer = customers.requireCustomer(userId);
        Delivery delivery = request.deliveryId() == null ? null
                : deliveries.findByIdAndCustomerId(request.deliveryId(), customer.getId())
                    .orElseThrow(() -> new NotFoundException("Delivery not found"));
        SupportTicket ticket = new SupportTicket(customer, delivery, request.category(), request.subject().trim());
        ticket.addMessage(new SupportMessage(request.message().trim()));
        return SupportTicketResponse.from(tickets.saveAndFlush(ticket));
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> list(UUID userId) {
        Customer customer = customers.requireCustomer(userId);
        return tickets.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(SupportTicketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse details(UUID userId, Long ticketId) {
        Customer customer = customers.requireCustomer(userId);
        return SupportTicketResponse.from(findOwned(ticketId, customer.getId()));
    }

    public SupportTicketResponse addMessage(UUID userId, Long ticketId, AddSupportMessageRequest request) {
        Customer customer = customers.requireCustomer(userId);
        SupportTicket ticket = findOwned(ticketId, customer.getId());
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            throw new ConflictException("This ticket has been resolved. Please open a new support request.");
        }
        ticket.addMessage(new SupportMessage(request.message().trim()));
        return SupportTicketResponse.from(tickets.saveAndFlush(ticket));
    }

    private SupportTicket findOwned(Long ticketId, Long customerId) {
        return tickets.findByIdAndCustomerId(ticketId, customerId)
                .orElseThrow(() -> new NotFoundException("Support request not found"));
    }
}
