package com.swiftdrop.backend.dispatcher.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * One delivery with its courier assignment history (newest first). The delivery's fields are at the top level,
 * next to {@code assignments}.
 */
public record DispatchDeliveryDetailsResponse(
        @JsonUnwrapped DispatchDeliveryResponse delivery,
        List<AssignmentResponse> assignments) { }
