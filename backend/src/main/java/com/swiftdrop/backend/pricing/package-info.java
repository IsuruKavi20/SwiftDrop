/**
 * Pricing: the delivery tariff admins publish (Pricing page, /api/admin/pricing).
 * Front door: this base package ({@link com.swiftdrop.backend.pricing.Tariff} and
 * {@link com.swiftdrop.backend.pricing.TariffSource}, and {@link com.swiftdrop.backend.pricing.DistanceSource} for the
 * Google Maps road distance), which the customer module quotes with. Everything else is private.
 * See docs/PRICING-AND-ZONES-API.md.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Pricing")
package com.swiftdrop.backend.pricing;
