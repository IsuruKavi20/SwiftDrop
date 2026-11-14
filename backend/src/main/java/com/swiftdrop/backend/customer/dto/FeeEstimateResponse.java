package com.swiftdrop.backend.customer.dto;
import java.math.BigDecimal;
/** Transparent provisional quote; rates are configurable demo rules until the admin pricing module is integrated. */
public record FeeEstimateResponse(String currency, BigDecimal baseFee, BigDecimal weightCharge,
                                  BigDecimal serviceCharge, BigDecimal totalFee, String explanation) {}
