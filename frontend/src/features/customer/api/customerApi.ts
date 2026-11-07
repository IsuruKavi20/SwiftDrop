import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';

/** Mirrors the backend's Customer Module DTOs (com.swiftdrop.backend.customer.dto). */
export type DeliveryStatus =
  | 'PENDING' | 'CONFIRMED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

export type DeliveryType = 'STANDARD' | 'EXPRESS' | 'SAME_DAY';

export interface DeliverySummary {
  id: number;
  trackingNumber: string;
  deliveryType: DeliveryType;
  status: DeliveryStatus;
  receiverName: string | null;
  destinationCity: string | null;
  estimatedDeliveryDate: string | null;
  createdAt: string;
}

export interface Dashboard {
  totalDeliveries: number;
  activeDeliveries: number;
  countsByStatus: Record<DeliveryStatus, number>;
  recentDeliveries: DeliverySummary[];
}

export interface CustomerProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  createdAt: string;
}

export interface RegisterCustomer {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
}

/** A backend error: its "detail" message, and for invalid forms the message per field. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.apiUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    });
  } catch {
    throw new ApiError(0, `Cannot reach the SwiftDrop server at ${env.apiUrl}. Is the backend running?`);
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(response.status, problem?.detail ?? 'Something went wrong. Please try again.', problem?.errors);
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

export function getDashboard(): Promise<Dashboard> {
  return request<Dashboard>('/api/customers/me/dashboard');
}

export function getProfile(): Promise<CustomerProfile> {
  return request<CustomerProfile>('/api/customers/me');
}

export function registerCustomer(details: RegisterCustomer): Promise<CustomerProfile> {
  return request<CustomerProfile>('/api/customers/me', { method: 'POST', body: JSON.stringify(details) });
}

export const STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};
