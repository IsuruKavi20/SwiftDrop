package com.swiftdrop.backend.dispatcher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create or edit a zone. The code is stored in capitals; districts are Sri Lankan district names (any letter case). */
public record DeliveryZoneRequest(
        @NotBlank(message = "Enter a zone name")
        @Size(max = 60, message = "The name is at most 60 characters")
        String name,
        @NotBlank(message = "Enter a code")
        @Pattern(regexp = "^\\s*[A-Za-z]{2,4}\\s*$", message = "The code is 2 to 4 letters, e.g. WP")
        String code,
        @NotEmpty(message = "Choose at least one district")
        @Size(max = 25, message = "Sri Lanka has 25 districts")
        List<String> districts,
        @NotNull(message = "Say whether the zone is active")
        Boolean active) { }
