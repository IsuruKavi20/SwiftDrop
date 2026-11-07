
package com.swiftdrop.backend.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantUpdateRequest(

        @NotBlank
        @Size(max = 150)
        String businessName,

        @NotBlank
        @Email
        @Size(max = 150)
        String businessEmail,

        @NotBlank
        @Size(max = 20)
        String contactNumber,

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank
        @Size(max = 100)
        String city,

        @Size(max = 100)
        String province,

        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Size(max = 100)
        String country
) {}
