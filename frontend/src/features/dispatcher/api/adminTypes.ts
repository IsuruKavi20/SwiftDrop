import type { CourierAvailability, DeliveryType } from './dispatcherTypes';

/**
 * Shapes for the admin screens. Users, roles and merchant applications live in the auth service
 * (docs/auth-api/admin.md, merchants.md); courier profiles in the backend's dispatcher module (courier table);
 * prices in the backend's customer.service.CustomerPricingService. Zones have no backend counterpart yet.
 */

/**
 * Roles an admin hands out. The auth service also knows DISPATCHER, but in SwiftDrop admins do the dispatch work,
 * so it is not offered here.
 */
export type AdminRole = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'ADMIN';

/** One person in the auth service (GET /api/v1/admin/users/{userId}, GET /api/v1/admin/access-review). */
export interface AdminUser {
  id: string;
  name: string;
  email: string;
  roles: AdminRole[];
  /** False once an admin blocked the person (POST .../block); they can't log in until unblocked. */
  active: boolean;
}

export type MerchantApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

/** A merchant application (POST /api/v1/merchants/application, GET /api/v1/admin/merchants). */
export interface MerchantApplication {
  merchantId: string;
  businessName: string;
  registrationNumber: string;
  website: string | null;
  applicantName: string;
  applicantEmail: string;
  submittedAt: string;
  status: MerchantApplicationStatus;
  decidedAt: string | null;
  /** Why it was rejected (required for REJECT). */
  reason: string | null;
}

/** A courier's work profile (dispatcher.entity.Courier); name and email come from the person (AppUser). */
export interface CourierProfile {
  courierId: number;
  userId: string;
  name: string;
  email: string;
  phoneNumber: string;
  vehicleType: string | null;
  vehicleNumber: string | null;
  availability: CourierAvailability;
  activeDeliveries: number;
}

export interface CourierProfileInput {
  phoneNumber: string;
  vehicleType: string;
  vehicleNumber: string;
  availability: CourierAvailability;
}

export interface DeliveryZone {
  id: number;
  name: string;
  code: string;
  districts: string[];
  active: boolean;
}

export type DeliveryZoneInput = Omit<DeliveryZone, 'id'>;

/** The tariff CustomerPricingService quotes with (swiftdrop.customer.pricing.* settings), all in LKR. */
export interface PricingRates {
  sameDistrictBase: number;
  otherDistrictBase: number;
  /** Charged for each kg of total parcel weight above the first kg. */
  additionalKg: number;
  expressSurcharge: number;
  sameDaySurcharge: number;
}

export interface PublishedPricing {
  version: number;
  publishedAt: string;
  rates: PricingRates;
}

export interface QuoteRequest {
  pickupDistrict: string;
  destinationDistrict: string;
  totalWeightKg: number;
  deliveryType: DeliveryType;
}

/** Same parts as customer.dto.FeeEstimateResponse. */
export interface Quote {
  baseFee: number;
  weightCharge: number;
  serviceCharge: number;
  totalFee: number;
}

export interface DayCount {
  /** yyyy-mm-dd, local time */
  day: string;
  delivered: number;
  failedAttempts: number;
}

export interface CourierReport {
  courierId: number;
  name: string;
  delivered: number;
  failedAttempts: number;
  active: number;
}

export interface DeliveryReport {
  bookingsCreated: number;
  delivered: number;
  failedAttempts: number;
  perDay: DayCount[];
  couriers: CourierReport[];
}
