import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { ApiError } from '../../customer/api/customerApi';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface NotificationItem {
  id: number;
  deliveryId: number | null;
  trackingNumber: string | null;
  channel: NotificationChannel;
  eventType: string;
  subject: string | null;
  content: string;
  status: NotificationStatus;
  createdAt: string;
  sentAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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

/** Fetches paginated notifications for the authenticated user. */
export function getNotifications(page = 0, size = 10): Promise<PageResponse<NotificationItem>> {
  return request<PageResponse<NotificationItem>>(`/api/customers/me/notifications?page=${page}&size=${size}`);
}

/** Fetches notification count for the navbar badge. */
export function getNotificationCount(): Promise<{ count: number }> {
  return request<{ count: number }>('/api/customers/me/notifications/count');
}

/** Dev / Demo helper: triggers a test notification to appear in the UI immediately. */
export function triggerTestNotification(trackingNumber?: string, status?: string): Promise<NotificationItem> {
  const params = new URLSearchParams();
  if (trackingNumber) params.append('trackingNumber', trackingNumber);
  if (status) params.append('status', status);
  const query = params.toString() ? `?${params.toString()}` : '';
  return request<NotificationItem>(`/api/customers/me/notifications/test-trigger${query}`, { method: 'POST' });
}

