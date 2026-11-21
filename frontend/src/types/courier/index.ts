/**
 * SwiftDrop Courier Module - TypeScript Type Definitions
 * Aligned with Spring Boot DTO contracts and Courier UI/UX.
 */

export type ParcelStatus = 
  | 'PENDING'
  | 'CONFIRMED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export type FailureReason =
  | 'RECIPIENT_UNAVAILABLE'
  | 'INCORRECT_ADDRESS'
  | 'CUSTOMER_REJECTED'
  | 'DAMAGED_PARCEL'
  | 'ACCESS_RESTRICTED'
  | 'WEATHER_DELAY'
  | 'OTHER';

export type StatusFilterOption = 'ALL' | ParcelStatus;

export interface DeliverySummary {
  id: string;
  trackingNumber: string;
  recipientName: string;
  recipientPhone: string;
  deliveryAddress: string;
  city: string;
  postalCode: string;
  status: ParcelStatus;
  specialInstructions?: string;
  assignedAt: string;
  isToday: boolean;
}

export interface StatusHistoryItem {
  id: string;
  fromStatus?: ParcelStatus;
  toStatus: ParcelStatus;
  changedBy: string;
  timestamp: string;
  notes?: string;
}

export interface DeliveryProof {
  id: string;
  proofType: string;
  recipientSignName: string;
  deliveryNotes?: string;
  proofMediaUrl?: string;
  capturedAt: string;
}

export interface DeliveryFailure {
  id: string;
  failureReason: string;
  failureRemarks: string;
  reportedAt: string;
}

export interface DeliveryDetails extends DeliverySummary {
  statusHistory: StatusHistoryItem[];
  proof?: DeliveryProof;
  failure?: DeliveryFailure;
}

export interface DashboardStats {
  assignedToday: number;
  inProgress: number;
  delivered: number;
  failed: number;
  completionPercentage: number;
}

export interface CourierProfile {
  id: string;
  fullName: string;
  email: string;
  phoneNumber: string;
  vehicleType: string;
  assignedZone: string;
  isActive: boolean;
  avatarUrl?: string;
}

export interface ActivityUpdate {
  id: string;
  trackingNumber: string;
  recipientName: string;
  status: ParcelStatus;
  timestamp: string;
  location?: string;
}

export interface StatusUpdateRequest {
  nextStatus: ParcelStatus;
  notes?: string;
  location?: string;
}

export interface DeliveryConfirmationRequest {
  recipientSignName: string;
  proofType?: string;
  deliveryNotes?: string;
  proofMediaUrl?: string;
  location?: string;
}

export interface DeliveryFailureRequest {
  failureReason: FailureReason | string;
  failureRemarks: string;
  location?: string;
}

export interface CourierDataService {
  getDashboardStats(): Promise<DashboardStats>;
  getAssignedDeliveries(filter?: { status?: string; search?: string }): Promise<DeliverySummary[]>;
  getDeliveryDetails(trackingNumber: string): Promise<DeliveryDetails | null>;
  getDeliveryHistory(): Promise<DeliverySummary[]>;
  getCourierProfile(): Promise<CourierProfile>;
  updateCourierProfile(request: { phoneNumber?: string; vehicleType?: string }): Promise<CourierProfile>;
  getRecentUpdates(): Promise<ActivityUpdate[]>;
  updateDeliveryStatus(trackingNumber: string, request: StatusUpdateRequest): Promise<DeliveryDetails>;
  confirmDelivery(trackingNumber: string, request: DeliveryConfirmationRequest): Promise<DeliveryDetails>;
  reportFailure(trackingNumber: string, request: DeliveryFailureRequest): Promise<DeliveryDetails>;
}
