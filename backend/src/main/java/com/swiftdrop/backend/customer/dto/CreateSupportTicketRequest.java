package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.SupportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotNull SupportCategory category,
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 2000) String message,
        Long deliveryId) { }
