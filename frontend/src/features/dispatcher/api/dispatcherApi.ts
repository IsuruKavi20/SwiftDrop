import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { DISPATCH_CHANGED_EVENT, DispatchError, usingSampleData } from './dispatchCommon';
import type {
  CourierAvailability,
  CourierWorkload,
  DeliveryDetails,
  DeliverySearch,
  DeliveryStatus,
  DispatchDelivery,
  Page,
  StatusCounts,
} from './dispatcherTypes';
import * as sample from './sampleDispatcherApi';

export { DISPATCH_CHANGED_EVENT, DispatchError, setDispatchSource, usingSampleData } from './dispatchCommon';

/**
 * Everything the dispatch screens read and change goes through this file. Signed-in admins get the backend's
 * dispatcher endpoints (com.swiftdrop.backend.dispatcher.controller.DispatcherController, ADMIN only); the
 * development preview answers from sample data (sampleDispatcherApi.ts).
 */

const base = () => `${env.apiUrl}/api/dispatcher`;

/** The backend's shared.dto.PageResponse */
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Problem JSON from the backend (status, code, detail, errors), as a DispatchError with a sentence to show. */
async function failure(response: Response): Promise<never> {
  let detail: string | undefined;
  try {
    const problem = (await response.json()) as { detail?: string; errors?: Record<string, string> };
    detail = Object.values(problem.errors ?? {})[0] ?? problem.detail;
  } catch {
    // not JSON: use the generic sentence below
  }
  if (response.status === 401) detail = 'Your session has ended. Sign in again.';
  if (response.status === 403) detail = 'Only admins can use the dispatch screens.';
  throw new DispatchError(detail ?? 'Something went wrong. Please try again.');
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${base()}${path}`, init);
  } catch {
    throw new DispatchError('Could not reach the SwiftDrop server. Check your connection and try again.');
  }
  if (!response.ok) return failure(response);
  return (await response.json()) as T;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

function toPage<T>(page: PageResponse<T>): Page<T> {
  return { items: page.content, page: page.page, size: page.size, totalItems: page.totalElements, totalPages: page.totalPages };
}

/** Deliveries per status across all customers and merchants (every status present). */
export async function getStatusCounts(): Promise<StatusCounts> {
  if (usingSampleData()) return sample.getStatusCounts();
  const counts = await request<{ statuses: StatusCounts; unassigned: number }>('/deliveries/counts');
  return counts.statuses;
}

/** Confirmed deliveries still waiting for a courier. */
export async function countUnassignedDeliveries(): Promise<number> {
  if (usingSampleData()) return sample.countUnassignedDeliveries();
  const counts = await request<{ statuses: StatusCounts; unassigned: number }>('/deliveries/counts');
  return counts.unassigned;
}

/** Optional status and text (tracking number, courier, contacts, places), newest bookings first, paged (max 50). */
export async function searchDeliveries(search: DeliverySearch = {}): Promise<Page<DispatchDelivery>> {
  if (usingSampleData()) return sample.searchDeliveries(search);
  const { status, text, page = 0, size = 10 } = search;
  return toPage(await request<PageResponse<DispatchDelivery>>(`/deliveries${query({ status, search: text, page, size })}`));
}

/** Confirmed bookings with no courier yet, oldest first (the first 50). */
export async function listUnassignedDeliveries(text = ''): Promise<DispatchDelivery[]> {
  if (usingSampleData()) return sample.listUnassignedDeliveries(text);
  const page = await request<PageResponse<DispatchDelivery>>(`/deliveries/unassigned${query({ search: text, size: 50 })}`);
  return page.content;
}

export async function getDeliveryDetails(deliveryId: number): Promise<DeliveryDetails> {
  if (usingSampleData()) return sample.getDeliveryDetails(deliveryId);
  return request<DeliveryDetails>(`/deliveries/${deliveryId}`);
}

/** Courier profiles with their active-assignment workload; optionally one availability. */
export async function listCourierWorkloads(availability?: CourierAvailability): Promise<CourierWorkload[]> {
  if (usingSampleData()) return sample.listCourierWorkloads(availability);
  return request<CourierWorkload[]>(`/couriers${query({ availability })}`);
}

/** Who can take a delivery now: Available couriers with a usable Courier account, lightest workload first. */
export async function listAssignableCouriers(): Promise<CourierWorkload[]> {
  if (usingSampleData()) return sample.listAssignableCouriers();
  return request<CourierWorkload[]>('/couriers/assignable');
}

/**
 * Assigns a courier, or reassigns (then `reason` is required and kept with the ended assignment).
 * The delivery's status is not changed; the courier records the next step.
 */
export async function assignCourier(deliveryId: number, courierId: number, reason?: string): Promise<void> {
  if (usingSampleData()) return sample.assignCourier(deliveryId, courierId, reason);
  await request<DeliveryDetails>(`/deliveries/${deliveryId}/assignment`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ courierId, reason: reason?.trim() || undefined }),
  });
  window.dispatchEvent(new Event(DISPATCH_CHANGED_EVENT));
}

export type { DeliveryStatus };
