package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.Size;

/** Optional customer cancellation reason; never accepts client-supplied status or owner id. */
public record CancelDeliveryRequest(@Size(max = 200) String reason) { }
