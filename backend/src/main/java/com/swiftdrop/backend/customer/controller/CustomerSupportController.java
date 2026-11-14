package com.swiftdrop.backend.customer.controller;

import com.swiftdrop.backend.customer.dto.AddSupportMessageRequest;
import com.swiftdrop.backend.customer.dto.CreateSupportTicketRequest;
import com.swiftdrop.backend.customer.dto.SupportTicketResponse;
import com.swiftdrop.backend.customer.service.CustomerSupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customers/me/support")
@PreAuthorize("hasAuthority('parcel:read-own')")
public class CustomerSupportController {
    private final CustomerSupportService tickets;
    public CustomerSupportController(CustomerSupportService tickets) { this.tickets = tickets; }

    @GetMapping
    public List<SupportTicketResponse> list(@AuthenticationPrincipal Jwt user) {
        return tickets.list(CurrentUser.id(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketResponse create(@Valid @RequestBody CreateSupportTicketRequest request,
                                        @AuthenticationPrincipal Jwt user) {
        return tickets.create(CurrentUser.id(user), request);
    }

    @GetMapping("/{id}")
    public SupportTicketResponse details(@PathVariable Long id, @AuthenticationPrincipal Jwt user) {
        return tickets.details(CurrentUser.id(user), id);
    }

    @PostMapping("/{id}/messages")
    public SupportTicketResponse addMessage(@PathVariable Long id,
                                            @Valid @RequestBody AddSupportMessageRequest request,
                                            @AuthenticationPrincipal Jwt user) {
        return tickets.addMessage(CurrentUser.id(user), id, request);
    }
}
