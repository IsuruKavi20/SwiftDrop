import type { AdminRole, MerchantApplicationStatus } from '../api/adminTypes';
import type { CourierAvailability, DeliverySource, DeliveryStatus, DispatchDelivery, DispatchParcel } from '../api/dispatcherTypes';

export const statusLabels: Record<DeliveryStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export const availabilityLabels: Record<CourierAvailability, string> = {
  AVAILABLE: 'Available',
  BUSY: 'Busy',
  OFFLINE: 'Offline',
};

export const sourceLabels: Record<DeliverySource, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
};

/** Bookings waiting longer than this for a courier are flagged on the overview. */
export const WAITING_ALERT_HOURS = 2;

const dateTime = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const dateOnly = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const todayLong = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

export const formatDateTime = (iso: string) => dateTime.format(new Date(iso));
export const formatDate = (iso: string) => dateOnly.format(new Date(iso));
export const formatToday = () => todayLong.format(new Date());

export function formatFee(fee: number | null): string {
  return fee === null ? '—' : `LKR ${fee.toLocaleString('en-LK', { maximumFractionDigits: 2 })}`;
}

export function totalWeight(parcels: DispatchParcel[]): string {
  const kg = parcels.reduce((sum, p) => sum + p.weightKg, 0);
  return `${Number(kg.toFixed(2))} kg`;
}

export function parcelSummary(parcels: DispatchParcel[]): string {
  const count = parcels.length === 1 ? '1 parcel' : `${parcels.length} parcels`;
  return `${totalWeight(parcels)} · ${count}`;
}

export function hoursSince(iso: string): number {
  return (Date.now() - new Date(iso).getTime()) / 3_600_000;
}

export function waitingLabel(iso: string): string {
  const hours = hoursSince(iso);
  if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} min`;
  const whole = Math.floor(hours);
  const minutes = Math.round((hours - whole) * 60);
  return minutes ? `${whole} h ${minutes} min` : `${whole} h`;
}

/** The newest FAILED tracking event says why the attempt failed (Delivery has no separate reason field). */
export function latestFailure(delivery: DispatchDelivery) {
  const failures = delivery.trackingEvents.filter((e) => e.status === 'FAILED');
  const latest = failures.reduce<(typeof failures)[number] | undefined>(
    (newest, e) => (!newest || e.eventTime > newest.eventTime ? e : newest), undefined);
  return { latest, attempts: failures.length };
}

export const roleLabels: Record<AdminRole, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  ADMIN: 'Admin',
};

export const merchantStatusLabels: Record<MerchantApplicationStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

/** Delivered ÷ (delivered + failed); null when there is nothing to divide. */
export function successRate(delivered: number, failed: number): number | null {
  return delivered + failed === 0 ? null : delivered / (delivered + failed);
}

export function formatPercent(rate: number | null): string {
  return rate === null ? '—' : `${(rate * 100).toFixed(1)}%`;
}

export function formatLkr(amount: number): string {
  return `LKR ${amount.toLocaleString('en-LK', { maximumFractionDigits: 2 })}`;
}
