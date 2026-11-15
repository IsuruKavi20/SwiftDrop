package com.swiftdrop.backend.customer.dto;
import java.math.BigDecimal;
/** Transparent quote with the published tariff (pricing module); {@code explanation} says which rates were used. */
public record FeeEstimateResponse(String currency, BigDecimal baseFee, BigDecimal weightCharge,
                                  BigDecimal serviceCharge, BigDecimal totalFee, String explanation) {}
