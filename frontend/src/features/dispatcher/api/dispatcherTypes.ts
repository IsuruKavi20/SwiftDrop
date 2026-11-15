import type { DeliveryStatus, DeliveryType } from '../../customer/api/customerApi';

/**
 * Shapes for the dispatcher screens, built from the backend's dispatcher module
 * (com.swiftdrop.backend.dispatcher: Courier, CourierAssignment, the three repositories) and the
 * delivery model it reuses (com.swiftdrop.backend.customer.entity: Delivery, DeliveryAddress, Parcel, TrackingEvent).
 * Field names follow the existing customer DTOs so the future dispatcher DTOs can use the same ones.
 */
export type { DeliveryStatus, DeliveryType };

/** dispatcher.entity.CourierAvailability */
export type CourierAvailability = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

/** A delivery belongs to a customer (customer_id) or a merchant (merchant_id). */
export type DeliverySource = 'CUSTOMER' | 'MERCHANT';

/** customer.dto.DeliveryAddressResponse */
export interface DispatchAddress {
  contactName: string;
  contactPhone: string;
  street: string;
  city: string;
  district: string;
  postalCode: string | null;
}

/** customer.dto.ParcelResponse */
export interface DispatchParcel {
  id: number;
  description: string;
  weightKg: number;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
  declaredValue: number | null;
}

/** customer.dto.TrackingEventResponse */
export interface DispatchTrackingEvent {
  status: DeliveryStatus;
  description: string;
  locationName: string | null;
  eventTime: string;
}

/** The courier currently assigned to a delivery (Delivery.courier and the active CourierAssignment). */
export interface AssignedCourier {
  /** The courier profile id; null for a courier set on the delivery before courier profiles existed. */
  courierId: number | null;
  name: string;
}

/** One row of the cross-customer delivery lists (DispatcherDeliveryRepository.search). */
export interface DispatchDelivery {
  id: number;
  trackingNumber: string;
  deliveryType: DeliveryType;
  status: DeliveryStatus;
  source: DeliverySource;
  estimatedFee: number | null;
  specialInstructions: string | null;
  createdAt: string;
  deliveredAt: string | null;
  pickup: DispatchAddress;
  destination: DispatchAddress;
  parcels: DispatchParcel[];
  trackingEvents: DispatchTrackingEvent[];
  courier: AssignedCourier | null;
}

/** One CourierAssignment row: the active one has no end, history rows say who ended them and why. */
export interface AssignmentRecord {
  assignmentId: number;
  courierId: number;
  courierName: string;
  assignedByName: string;
  assignedAt: string;
  active: boolean;
  endedByName: string | null;
  endedAt: string | null;
  endReason: string | null;
}

export interface DeliveryDetails extends DispatchDelivery {
  /** Newest first (CourierAssignmentRepository.findByDeliveryIdOrderByAssignedAtDescIdDesc). */
  assignments: AssignmentRecord[];
}

/** A Courier profile with its name from AppUser and its workload (active assignments only). */
export interface CourierWorkload {
  courierId: number;
  name: string;
  phoneNumber: string;
  vehicleType: string | null;
  vehicleNumber: string | null;
  availability: CourierAvailability;
  activeDeliveries: number;
  /** Tracking numbers of the courier's active assignments, oldest first. */
  currentTrackingNumbers: string[];
}

/** DispatcherDeliveryRepository.countByStatus: statuses with no deliveries are 0. */
export type StatusCounts = Record<DeliveryStatus, number>;

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface DeliverySearch {
  status?: DeliveryStatus;
  /** Matches tracking numbers, courier names, contact names, streets, cities and districts. */
  text?: string;
  page?: number;
  size?: number;
}
