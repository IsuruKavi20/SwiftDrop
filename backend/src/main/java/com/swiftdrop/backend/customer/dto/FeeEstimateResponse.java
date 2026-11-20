package com.swiftdrop.backend.customer.dto;

import java.math.BigDecimal;

/**
 * Transparent quote with the published tariff (pricing module); {@code explanation} says which rates were used.
 *
 * @param distanceKm     the road distance Google Maps found, in km; null when it couldn't be found and the
 *                       district base was used instead
 * @param routeFrom      the pickup address exactly as it was given to Google Maps, so the customer can check the
 *                       route on Google Maps
 * @param routeTo        the destination address exactly as it was given to Google Maps
 * @param baseFee        the distance base fee (covers the first km), or the district base when distanceKm is null
 * @param distanceCharge the per-km charge for the km after those the base fee covers; 0 when distanceKm is null
 */
public record FeeEstimateResponse(String currency, BigDecimal distanceKm,
                                  String routeFrom, String routeTo, BigDecimal baseFee,
                                  BigDecimal distanceCharge, BigDecimal weightCharge, BigDecimal serviceCharge,
                                  BigDecimal totalFee, String explanation) {}
