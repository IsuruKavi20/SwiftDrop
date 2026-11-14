import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { MerchantApiError } from './merchantApi';

export type DeliveryStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export type DeliveryType = 'STANDARD' | 'EXPRESS' | 'SAME_DAY';

export interface DeliveryAddress {
  contactName: string;
  contactPhone: string;
  street: string;
  city: string;
  district: string;
  postalCode?: string | null;
}

export interface Parcel {
  id?: number;
  description: string;
  weightKg: number;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
  declaredValue?: number | null;
}

export interface TrackingEvent {
  id?: number;
  status: DeliveryStatus;
  description: string;
  locationName?: string | null;
  eventTime: string;
  recordedByName?: string | null;
}

export interface MerchantDeliverySummary {
  id: number;
  trackingNumber: string;
  deliveryType: DeliveryType;
  status: DeliveryStatus;
  receiverName: string | null;
  destinationCity: string | null;
  estimatedDeliveryDate: string | null;
  createdAt: string;
}

export interface MerchantDeliveryDetail {
  id: number;
  trackingNumber: string;
  publicTrackingToken: string;
  deliveryType: DeliveryType;
  status: DeliveryStatus;
  specialInstructions?: string | null;
  estimatedDeliveryDate: string | null;
  createdAt: string;
  deliveredAt?: string | null;
  pickup: DeliveryAddress;
  destination: DeliveryAddress;
  parcels: Parcel[];
  trackingEvents: TrackingEvent[];
}

export interface MerchantDashboardData {
  businessName: string | null;
  profileComplete: boolean;
  totalOrders: number;
  pendingOrders: number;
  inTransitOrders: number;
  deliveredOrders: number;
  countsByStatus: Record<DeliveryStatus, number>;
  recentOrders: MerchantDeliverySummary[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateMerchantDeliveryInput {
  deliveryType: DeliveryType;
  pickup: DeliveryAddress;
  destination: DeliveryAddress;
  parcels: Parcel[];
  specialInstructions?: string | null;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.apiUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    });
  } catch {
    throw new MerchantApiError(0, `Cannot reach the SwiftDrop server at ${env.apiUrl}. Is the backend running?`);
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as Record<string, unknown> | null;

    const fieldErrors: Record<string, string> = {};
    if (problem && typeof problem.errors === 'object' && problem.errors !== null) {
      for (const [key, val] of Object.entries(problem.errors as Record<string, unknown>)) {
        if (typeof val === 'string') {
          fieldErrors[key] = val;
        }
      }
    }

    const message =
      (typeof problem?.detail === 'string' && problem.detail.trim().length > 0 ? problem.detail : null) ??
      (typeof problem?.title === 'string' && problem.title.trim().length > 0 ? problem.title : null) ??
      (typeof problem?.message === 'string' && problem.message.trim().length > 0 ? problem.message : null) ??
      (response.status === 403
        ? 'Access denied. You do not have permission to access merchant orders.'
        : response.status === 401
          ? 'Your session has expired. Please log in again.'
          : response.status === 400
            ? 'Some fields are invalid. Please check your entries and try again.'
            : response.status === 404
              ? 'Delivery order not found.'
              : 'Something went wrong. Please try again.');

    throw new MerchantApiError(response.status, message, fieldErrors);
  }

  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** Fetches dashboard metrics, order stats and recent bookings for the merchant. */
export function getMerchantDashboard(): Promise<MerchantDashboardData> {
  return request<MerchantDashboardData>('/api/merchants/me/dashboard');
}

/** Fetches a paginated list of deliveries with optional status filter. */
export function getMerchantDeliveries(
  status?: DeliveryStatus | 'ALL',
  page = 0,
  size = 10
): Promise<PageResponse<MerchantDeliverySummary>> {
  const params = new URLSearchParams();
  if (status && status !== 'ALL') {
    params.set('status', status);
  }
  params.set('page', String(page));
  params.set('size', String(size));

  return request<PageResponse<MerchantDeliverySummary>>(`/api/merchants/me/deliveries?${params.toString()}`);
}

/** Fetches detailed order information including pickup, destination, parcel specs, and tracking events. */
export function getMerchantDelivery(id: number): Promise<MerchantDeliveryDetail> {
  return request<MerchantDeliveryDetail>(`/api/merchants/me/deliveries/${id}`);
}

/** Books a new delivery order for the merchant. */
export function createMerchantDelivery(input: CreateMerchantDeliveryInput): Promise<MerchantDeliveryDetail> {
  return request<MerchantDeliveryDetail>('/api/merchants/me/deliveries', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
