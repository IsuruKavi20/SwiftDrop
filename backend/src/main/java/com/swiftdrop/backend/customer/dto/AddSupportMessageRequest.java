package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddSupportMessageRequest(@NotBlank @Size(max = 2000) String message) { }
