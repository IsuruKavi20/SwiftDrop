import type {
  AssignmentRecord,
  CourierAvailability,
  CourierWorkload,
  DeliveryDetails,
  DeliverySearch,
  DeliveryStatus,
  DispatchDelivery,
  Page,
  StatusCounts,
} from './dispatcherTypes';
import { sampleAssignments, sampleCouriers, sampleDeliveries, type SampleDelivery } from './sampleData';

import { DISPATCH_CHANGED_EVENT, DispatchError } from './dispatchCommon';

/**
 * The dispatch screens' data in the browser, from sampleData.ts, applying the same rules as the backend.
 * Used by the development preview (/admin/preview), which has no sign-in; signed-in pages use the backend
 * through dispatcherApi.ts.
 */

const ALL_STATUSES: DeliveryStatus[] = [
  'PENDING', 'CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED',
];

/** Deliveries a dispatcher may assign or reassign: confirmed, under way, or failed (for another attempt). */
const ASSIGNABLE: DeliveryStatus[] = ['CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED'];

/** The signed-in admin, shown in assignment history for changes made in this tab. */
const CURRENT_ADMIN_NAME = 'You';

let nextAssignmentId = Math.max(...sampleAssignments.map((a) => a.assignmentId)) + 1;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 150));
}

function courierName(courierId: number): string {
  return sampleCouriers.find((c) => c.courierId === courierId)?.name ?? 'Unknown courier';
}

function toDelivery(row: SampleDelivery): DispatchDelivery {
  const { courierId, ...rest } = row;
  return { ...rest, courier: courierId === null ? null : { courierId, name: courierName(courierId) } };
}

/** Same fields as DispatcherDeliveryRepository.search: tracking number, courier name, contacts and addresses. */
function matchesText(row: SampleDelivery, text: string): boolean {
  const needle = text.trim().toLowerCase();
  if (!needle) return true;
  const fields = [row.trackingNumber, row.courierId === null ? '' : courierName(row.courierId)];
  for (const a of [row.pickup, row.destination]) fields.push(a.contactName, a.street, a.city, a.district);
  return fields.some((field) => field.toLowerCase().includes(needle));
}

function newestFirst(a: SampleDelivery, b: SampleDelivery): number {
  return b.createdAt.localeCompare(a.createdAt) || b.id - a.id;
}

/** DispatcherDeliveryRepository.countByStatus, with missing statuses as 0. */
export function getStatusCounts(): Promise<StatusCounts> {
  const counts = Object.fromEntries(ALL_STATUSES.map((s) => [s, 0])) as StatusCounts;
  for (const row of sampleDeliveries) counts[row.status] += 1;
  return delay(counts);
}

/** DispatcherDeliveryRepository.search: optional status and text, newest bookings first, paged. */
export function searchDeliveries({ status, text = '', page = 0, size = 10 }: DeliverySearch = {}): Promise<Page<DispatchDelivery>> {
  const rows = sampleDeliveries
    .filter((row) => (!status || row.status === status) && matchesText(row, text))
    .sort(newestFirst);
  const totalPages = Math.max(1, Math.ceil(rows.length / size));
  const current = Math.min(page, totalPages - 1);
  return delay({
    items: rows.slice(current * size, current * size + size).map(toDelivery),
    page: current,
    size,
    totalItems: rows.length,
    totalPages,
  });
}

/** Confirmed bookings with no courier yet, oldest first (they have waited longest). */
export function countUnassignedDeliveries(): Promise<number> {
  return delay(sampleDeliveries.filter((row) => row.status === 'CONFIRMED' && row.courierId === null).length);
}

export function listUnassignedDeliveries(text = ''): Promise<DispatchDelivery[]> {
  const rows = sampleDeliveries
    .filter((row) => row.status === 'CONFIRMED' && row.courierId === null && matchesText(row, text))
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  return delay(rows.map(toDelivery));
}

export function getDeliveryDetails(deliveryId: number): Promise<DeliveryDetails> {
  const row = sampleDeliveries.find((d) => d.id === deliveryId);
  if (!row) return Promise.reject(new DispatchError('This delivery no longer exists.'));
  const assignments: AssignmentRecord[] = sampleAssignments
    .filter((a) => a.deliveryId === deliveryId)
    .sort((a, b) => b.assignedAt.localeCompare(a.assignedAt) || b.assignmentId - a.assignmentId)
    .map((a) => ({
      assignmentId: a.assignmentId,
      courierId: a.courierId,
      courierName: courierName(a.courierId),
      assignedByName: a.assignedByName,
      assignedAt: a.assignedAt,
      active: a.active === true,
      endedByName: a.endedByName,
      endedAt: a.endedAt,
      endReason: a.endReason,
    }));
  return delay({ ...toDelivery(row), assignments });
}

/**
 * Courier profiles with their workload (CourierAssignmentRepository.countActiveByCourier: active assignments
 * only; couriers without any count as 0). Optionally only one availability (CourierRepository.findByAvailability...).
 */
export function listCourierWorkloads(availability?: CourierAvailability): Promise<CourierWorkload[]> {
  const workloads = sampleCouriers
    .filter((c) => !availability || c.availability === availability)
    .map((c) => {
      const active = sampleAssignments
        .filter((a) => a.active === true && a.courierId === c.courierId)
        .sort((a, b) => a.assignedAt.localeCompare(b.assignedAt) || a.assignmentId - b.assignmentId);
      const tracking = active.map((a) => sampleDeliveries.find((d) => d.id === a.deliveryId)?.trackingNumber ?? `#${a.deliveryId}`);
      return { ...c, activeDeliveries: active.length, currentTrackingNumbers: tracking };
    })
    .sort((a, b) => a.courierId - b.courierId);
  return delay(workloads);
}

/** Candidates for an assignment (CourierRepository.findAvailableForAssignment), lightest workload first. */
export async function listAssignableCouriers(): Promise<CourierWorkload[]> {
  const available = await listCourierWorkloads('AVAILABLE');
  return available.sort((a, b) => a.activeDeliveries - b.activeDeliveries || a.courierId - b.courierId);
}

/**
 * Assigns a courier, or reassigns: the current CourierAssignment is ended with the reason and a new active one
 * is created, and Delivery.courier follows it. A reassignment needs a reason (stored as end_reason).
 */
export function assignCourier(deliveryId: number, courierId: number, reason?: string): Promise<void> {
  const delivery = sampleDeliveries.find((d) => d.id === deliveryId);
  const courier = sampleCouriers.find((c) => c.courierId === courierId);
  if (!delivery) return Promise.reject(new DispatchError('This delivery no longer exists.'));
  if (!courier) return Promise.reject(new DispatchError('This courier no longer exists.'));
  if (!ASSIGNABLE.includes(delivery.status)) {
    return Promise.reject(new DispatchError('Only confirmed, in-progress or failed deliveries can be assigned.'));
  }
  if (courier.availability !== 'AVAILABLE') {
    return Promise.reject(new DispatchError(`${courier.name} is not available right now.`));
  }

  const current = sampleAssignments.find((a) => a.deliveryId === deliveryId && a.active === true);
  if (current?.courierId === courierId) {
    return Promise.reject(new DispatchError(`${courier.name} is already assigned to this delivery.`));
  }
  const trimmed = reason?.trim() ?? '';
  if (current && !trimmed) return Promise.reject(new DispatchError('Give a reason for the reassignment.'));
  if (trimmed.length > 500) return Promise.reject(new DispatchError('Keep the reason under 500 characters.'));

  const at = new Date().toISOString();
  if (current) {
    current.active = null;
    current.endedByName = CURRENT_ADMIN_NAME;
    current.endedAt = at;
    current.endReason = trimmed;
  }
  sampleAssignments.push({
    assignmentId: nextAssignmentId++, deliveryId, courierId, assignedByName: CURRENT_ADMIN_NAME, assignedAt: at,
    active: true, endedByName: null, endedAt: null, endReason: null,
  });
  delivery.courierId = courierId;
  window.dispatchEvent(new Event(DISPATCH_CHANGED_EVENT));
  return delay(undefined);
}
