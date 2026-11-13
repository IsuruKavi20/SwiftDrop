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
  /** From the login account (auth service) */
  name: string | null;
  email: string | null;
  pictureUrl: string | null;
  phoneNumber: string;
  createdAt: string;
}

/** Only what SwiftDrop needs: name and email already come from the login account. */
export interface RegisterCustomer {
  phoneNumber: string;
  fullName?: string;
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
  PENDING: 'Awaiting confirmation',
  CONFIRMED: 'Created',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};


// Customer booking and delivery contracts mirror existing Spring Boot endpoints.
export interface DeliveryAddressInput { contactName: string; contactPhone: string; street: string; city: string; district: string; postalCode: string }
export interface ParcelInput { description: string; weightKg: number; lengthCm: number; widthCm: number; heightCm: number; declaredValue?: number }
export interface BookingInput { deliveryType: DeliveryType; pickup: DeliveryAddressInput; destination: DeliveryAddressInput; parcels: ParcelInput[]; specialInstructions?: string }
export interface FeeEstimate { currency: string; baseFee: number; weightCharge: number; serviceCharge: number; totalFee: number; explanation: string }
export interface TrackingEvent { status: DeliveryStatus; description: string; locationName: string | null; eventTime: string }
export interface Tracking { trackingNumber: string; deliveryType: DeliveryType; status: DeliveryStatus; fromCity: string | null; toCity: string | null; estimatedDeliveryDate: string | null; deliveredAt: string | null; events: TrackingEvent[] }
export interface DeliveryDetails {
  id: number; trackingNumber: string; publicTrackingToken: string; deliveryType: DeliveryType;
  status: DeliveryStatus; specialInstructions: string | null; cancellationReason: string | null; estimatedFee: number | null;
  estimatedDeliveryDate: string | null; createdAt: string; deliveredAt: string | null;
  pickup: DeliveryAddressInput; destination: DeliveryAddressInput; parcels: (ParcelInput & {id: number})[];
  trackingEvents: TrackingEvent[];
}
export interface PagedDeliveries { content: DeliverySummary[]; page: number; size: number; totalElements: number; totalPages: number }
export interface SavedAddress { id: number; label: string; street: string; city: string; district: string; postalCode: string | null; defaultAddress: boolean }
export interface SavedAddressInput { label: string; street: string; city: string; district: string; postalCode?: string; defaultAddress: boolean }
export const estimateDelivery = (details: BookingInput) => request<FeeEstimate>('/api/customers/me/deliveries/estimate', {method:'POST', body:JSON.stringify(details)});
export const createDelivery = (details: BookingInput) => request<DeliveryDetails>('/api/customers/me/deliveries', {method:'POST', body:JSON.stringify(details)});
export const confirmDelivery = (id: number) => request<DeliveryDetails>(`/api/customers/me/deliveries/${id}/confirm`, {method:'POST'});
export const listDeliveries = (args: {page?:number;status?:string;search?:string}={}) => {
  const qs = new URLSearchParams({page:String(args.page??0),size:'10'});
  if(args.status)qs.set('status',args.status);
  if(args.search?.trim())qs.set('search',args.search.trim());
  return request<PagedDeliveries>(`/api/customers/me/deliveries?${qs}`);
};
export const getDelivery = (id: number) => request<DeliveryDetails>(`/api/customers/me/deliveries/${id}`);
export const trackMine = (code: string) => request<Tracking>(`/api/customers/me/tracking/${encodeURIComponent(code)}`);
export async function trackPublic(code: string): Promise<Tracking> {
  // Public endpoint must never be sent an expired authorization header.
  const response = await fetch(`${env.apiUrl}/api/public/tracking/${encodeURIComponent(code.trim())}`);
  if (!response.ok) throw new ApiError(response.status, response.status===404?'Tracking number not found.':'Unable to track this parcel.');
  return response.json() as Promise<Tracking>;
}
export async function downloadReceipt(id: number): Promise<void> {
  const response=await authorizedFetch(`${env.apiUrl}/api/customers/me/deliveries/${id}/receipt`);
  if(!response.ok) {const p=await response.json().catch(()=>null); throw new ApiError(response.status,p?.detail??'Receipt not available.');}
  const url=URL.createObjectURL(await response.blob());
  const link=document.createElement('a');link.href=url;link.download=`SwiftDrop-receipt-${id}.pdf`;
  document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);
}
export const updateCustomer = (phoneNumber:string, fullName:string) => request<CustomerProfile>('/api/customers/me',{method:'PUT',body:JSON.stringify({phoneNumber,fullName})});
export const getSavedAddresses = () => request<SavedAddress[]>('/api/customers/me/addresses');
export const addSavedAddress = (address:SavedAddressInput) => request<SavedAddress>('/api/customers/me/addresses',{method:'POST',body:JSON.stringify(address)});
export const updateSavedAddress = (id:number,address:SavedAddressInput) => request<SavedAddress>(`/api/customers/me/addresses/${id}`,{method:'PUT',body:JSON.stringify(address)});
export const deleteSavedAddress = (id:number) => request<void>(`/api/customers/me/addresses/${id}`,{method:'DELETE'});


/** Customer-owned booking cancellation; backend enforces status/assignment restrictions. */
export const cancelDelivery = (id: number, reason: string) =>
  request<DeliveryDetails>(`/api/customers/me/deliveries/${id}/cancel`, {
    method: 'POST', body: JSON.stringify({ reason }),
  });

/** Support cases are private to the signed-in customer. */
export type SupportCategory = 'DELIVERY_STATUS' | 'DELAY' | 'DAMAGED_PARCEL' | 'BILLING' | 'OTHER';
export type SupportTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export interface SupportTicket {
  id: number;
  category: SupportCategory;
  status: SupportTicketStatus;
  subject: string;
  deliveryId: number | null;
  trackingNumber: string | null;
  createdAt: string;
  updatedAt: string;
  messages: { id: number; senderRole: string; message: string; createdAt: string }[];
}
export interface CreateSupportTicket {
  category: SupportCategory;
  subject: string;
  message: string;
  deliveryId?: number;
}
export const listSupportTickets = () => request<SupportTicket[]>('/api/customers/me/support');
export const getSupportTicket = (id: number) => request<SupportTicket>(`/api/customers/me/support/${id}`);
export const createSupportTicket = (details: CreateSupportTicket) =>
  request<SupportTicket>('/api/customers/me/support', { method: 'POST', body: JSON.stringify(details) });
export const addSupportReply = (id: number, message: string) =>
  request<SupportTicket>(`/api/customers/me/support/${id}/messages`, {
    method: 'POST', body: JSON.stringify({ message }),
  });
