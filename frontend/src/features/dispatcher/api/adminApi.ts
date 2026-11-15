import { DISPATCH_CHANGED_EVENT, DispatchError } from './dispatchCommon';
import type {
  AdminRole,
  AdminUser,
  CourierProfile,
  CourierProfileInput,
  CourierReport,
  DayCount,
  DeliveryReport,
  DeliveryZone,
  DeliveryZoneInput,
  MerchantApplication,
  MerchantApplicationStatus,
  PricingRates,
  PublishedPricing,
  Quote,
  QuoteRequest,
} from './adminTypes';
import { sampleAssignments, sampleCouriers, sampleDeliveries } from './sampleData';

/**
 * Everything the admin screens read and change goes through this file. Like dispatcherApi.ts it answers from sample
 * data in the browser for now, applying the rules the real services enforce. Where each call goes later:
 * - users and roles: auth service, `${env.authUrl}/api/v1/admin/users...` and `/admin/access-review` (docs/auth-api/admin.md)
 * - merchant approvals: auth service, `${env.authUrl}/api/v1/admin/merchants...`
 * - courier profiles: the backend's dispatcher module (courier table; no endpoints yet)
 * - pricing: the backend's CustomerPricingService (rates are server settings today; no endpoint yet)
 * - zones and reports: no backend counterpart yet
 * Call auth with authorizedFetch and the admin's own token, so auth records which admin did what.
 */

const now = Date.now();
const hoursAgo = (hours: number) => new Date(now - hours * 3_600_000).toISOString();

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 150));
}

function changed() {
  window.dispatchEvent(new Event(DISPATCH_CHANGED_EVENT));
}

function fail(message: string): Promise<never> {
  return Promise.reject(new DispatchError(message));
}

/* ---------- Sample records ---------- */

const users: AdminUser[] = [
  { id: 'u-01', name: 'Amaya Ranasinghe', email: 'amaya.r@swiftdrop.example.lk', roles: ['ADMIN'], active: true },
  { id: 'u-02', name: 'Dinuka Abeysekera', email: 'dinuka.a@swiftdrop.example.lk', roles: ['ADMIN'], active: true },
  { id: 'u-03', name: 'Nimal Perera', email: 'nimal.perera@example.lk', roles: ['CUSTOMER'], active: true },
  { id: 'u-04', name: 'Priya Jayasinghe', email: 'priya.j@example.lk', roles: ['CUSTOMER'], active: true },
  { id: 'u-05', name: 'Tharindu Gunawardena', email: 'tharindu.g@example.lk', roles: ['CUSTOMER'], active: false },
  { id: 'u-06', name: 'Sanduni Wickramasinghe', email: 'sanduni@sandunifashion.example.lk', roles: ['CUSTOMER', 'MERCHANT'], active: true },
  { id: 'u-07', name: 'Ishara Mendis', email: 'ishara@ceylonteabox.example.lk', roles: ['CUSTOMER'], active: true },
  { id: 'u-08', name: 'Kasun Fernando', email: 'kasun.f@swiftdrop.example.lk', roles: ['COURIER'], active: true },
  { id: 'u-09', name: 'Ravi Silva', email: 'ravi.s@swiftdrop.example.lk', roles: ['COURIER'], active: true },
  { id: 'u-10', name: 'Chamara Bandara', email: 'chamara.b@swiftdrop.example.lk', roles: ['COURIER'], active: true },
  { id: 'u-11', name: 'Nadeesha Herath', email: 'nadeesha.h@swiftdrop.example.lk', roles: ['COURIER'], active: true },
  { id: 'u-12', name: 'Pathum Weerasinghe', email: 'pathum.w@swiftdrop.example.lk', roles: ['COURIER'], active: true },
];

/** courier.auth_user_id for the sample couriers in sampleData.ts */
const courierUserIds = new Map<number, string>([[1, 'u-08'], [2, 'u-09'], [3, 'u-10'], [4, 'u-11']]);

const merchantApplications: MerchantApplication[] = [
  {
    merchantId: 'm-02', businessName: 'Ceylon Tea Box', registrationNumber: 'PV00231877', website: 'https://ceylonteabox.example.lk',
    applicantName: 'Ishara Mendis', applicantEmail: 'ishara@ceylonteabox.example.lk', submittedAt: hoursAgo(20),
    status: 'PENDING', decidedAt: null, reason: null,
  },
  {
    merchantId: 'm-01', businessName: 'Sanduni Fashion', registrationNumber: 'PV00198432', website: null,
    applicantName: 'Sanduni Wickramasinghe', applicantEmail: 'sanduni@sandunifashion.example.lk', submittedAt: hoursAgo(400),
    status: 'APPROVED', decidedAt: hoursAgo(380), reason: null,
  },
];

/** Sri Lanka's 25 districts by province: what delivery addresses and pricing use. */
export const districtsByProvince: Record<string, string[]> = {
  Western: ['Colombo', 'Gampaha', 'Kalutara'],
  Central: ['Kandy', 'Matale', 'Nuwara Eliya'],
  Southern: ['Galle', 'Matara', 'Hambantota'],
  Northern: ['Jaffna', 'Kilinochchi', 'Mannar', 'Vavuniya', 'Mullaitivu'],
  Eastern: ['Batticaloa', 'Ampara', 'Trincomalee'],
  'North Western': ['Kurunegala', 'Puttalam'],
  'North Central': ['Anuradhapura', 'Polonnaruwa'],
  Uva: ['Badulla', 'Monaragala'],
  Sabaragamuwa: ['Ratnapura', 'Kegalle'],
};
export const allDistricts = Object.values(districtsByProvince).flat();

const zones: DeliveryZone[] = [
  { id: 1, name: 'Western Metro', code: 'WP', districts: ['Colombo', 'Gampaha', 'Kalutara'], active: true },
  { id: 2, name: 'Central Hills', code: 'CP', districts: ['Kandy', 'Matale', 'Nuwara Eliya'], active: true },
  { id: 3, name: 'Southern Coast', code: 'SP', districts: ['Galle', 'Matara', 'Hambantota'], active: true },
  { id: 4, name: 'North Western', code: 'NW', districts: ['Kurunegala', 'Puttalam'], active: true },
  { id: 5, name: 'Northern', code: 'NP', districts: ['Jaffna', 'Vavuniya'], active: false },
];
let nextZoneId = 6;

/** The backend's defaults (CustomerPricingService) as version 1. */
let pricing: PublishedPricing = {
  version: 1,
  publishedAt: hoursAgo(24 * 30),
  rates: { sameDistrictBase: 350, otherDistrictBase: 650, additionalKg: 120, expressSurcharge: 350, sameDaySurcharge: 650 },
};

/* ---------- Users and roles (auth service) ---------- */

export function listUsers(): Promise<AdminUser[]> {
  return delay([...users].sort((a, b) => a.name.localeCompare(b.name)));
}

/** PUT / DELETE /api/v1/admin/users/{userId}/roles/{role} for each role that changed. */
export function setUserRoles(userId: string, roles: AdminRole[]): Promise<void> {
  const user = users.find((u) => u.id === userId);
  if (!user) return fail('This account no longer exists.');
  if (roles.length === 0) return fail('Keep at least one role.');
  const losingAdmin = user.roles.includes('ADMIN') && !roles.includes('ADMIN');
  if (losingAdmin && users.filter((u) => u.roles.includes('ADMIN')).length === 1) {
    return fail('This is the last admin. Make someone else an admin first.');
  }
  if (user.roles.includes('COURIER') && !roles.includes('COURIER') && sampleCouriers.some((c) => courierUserIds.get(c.courierId) === userId)) {
    const active = sampleAssignments.filter((a) => a.active === true && courierUserIds.get(a.courierId) === userId).length;
    if (active > 0) return fail(`${user.name} still carries ${active} active ${active === 1 ? 'delivery' : 'deliveries'}. Reassign them first.`);
  }
  user.roles = [...roles];
  changed();
  return delay(undefined);
}

/** POST /api/v1/admin/users/{userId}/block or /unblock with { reason }. */
export function setUserBlocked(userId: string, blocked: boolean, reason: string): Promise<void> {
  const user = users.find((u) => u.id === userId);
  const trimmed = reason.trim();
  if (!user) return fail('This account no longer exists.');
  if (!trimmed) return fail('Give a reason; it is kept in the audit log.');
  if (trimmed.length > 500) return fail('Keep the reason under 500 characters.');
  if (blocked && user.roles.includes('ADMIN') && users.filter((u) => u.active && u.roles.includes('ADMIN')).length === 1) {
    return fail('This is the last active admin and cannot be blocked.');
  }
  user.active = !blocked;
  changed();
  return delay(undefined);
}

/* ---------- Merchant approvals (auth service) ---------- */

/** GET /api/v1/admin/merchants?status=... (newest first). */
export function listMerchantApplications(status?: MerchantApplicationStatus): Promise<MerchantApplication[]> {
  const rows = merchantApplications
    .filter((m) => !status || m.status === status)
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));
  return delay(rows);
}

/** POST /api/v1/admin/merchants/{merchantId}/decision { decision, reason }. Approval gives the MERCHANT role. */
export function decideMerchant(merchantId: string, decision: 'APPROVE' | 'REJECT', reason = ''): Promise<void> {
  const application = merchantApplications.find((m) => m.merchantId === merchantId);
  const trimmed = reason.trim();
  if (!application) return fail('This application no longer exists.');
  if (application.status !== 'PENDING') return fail('This application has already been decided.');
  if (decision === 'REJECT' && !trimmed) return fail('Tell the applicant why it was rejected.');
  if (trimmed.length > 500) return fail('Keep the reason under 500 characters.');
  application.status = decision === 'APPROVE' ? 'APPROVED' : 'REJECTED';
  application.decidedAt = new Date().toISOString();
  application.reason = trimmed || null;
  const owner = users.find((u) => u.email === application.applicantEmail);
  if (owner && decision === 'APPROVE' && !owner.roles.includes('MERCHANT')) owner.roles.push('MERCHANT');
  changed();
  return delay(undefined);
}

/** POST /api/v1/admin/merchants/{merchantId}/status { change: SUSPEND | REINSTATE }. */
export function setMerchantSuspended(merchantId: string, suspended: boolean): Promise<void> {
  const application = merchantApplications.find((m) => m.merchantId === merchantId);
  if (!application) return fail('This merchant no longer exists.');
  if (application.status !== (suspended ? 'APPROVED' : 'SUSPENDED')) {
    return fail(suspended ? 'Only approved merchants can be suspended.' : 'Only suspended merchants can be reinstated.');
  }
  application.status = suspended ? 'SUSPENDED' : 'APPROVED';
  changed();
  return delay(undefined);
}

/* ---------- Courier profiles (dispatcher module) ---------- */

function activeCount(courierId: number): number {
  return sampleAssignments.filter((a) => a.active === true && a.courierId === courierId).length;
}

export function listCourierProfiles(): Promise<CourierProfile[]> {
  const rows = sampleCouriers.map((c) => {
    const user = users.find((u) => u.id === courierUserIds.get(c.courierId));
    return { ...c, userId: user?.id ?? '', name: user?.name ?? c.name, email: user?.email ?? '', activeDeliveries: activeCount(c.courierId) };
  });
  return delay(rows.sort((a, b) => a.name.localeCompare(b.name)));
}

/** People with the COURIER role who have no work profile yet (a profile needs a COURIER account). */
export function listCourierAccountsWithoutProfile(): Promise<AdminUser[]> {
  const linked = new Set(courierUserIds.values());
  return delay(users.filter((u) => u.active && u.roles.includes('COURIER') && !linked.has(u.id)));
}

function checkProfile(input: CourierProfileInput): string | undefined {
  const phone = input.phoneNumber.trim();
  if (!phone) return 'Enter a phone number.';
  if (!/^\+?[0-9 ]{9,20}$/.test(phone)) return 'Enter a phone number of 9 to 20 digits.';
  if (input.vehicleType.trim().length > 30 || input.vehicleNumber.trim().length > 30) return 'Vehicle type and number are at most 30 characters.';
  return undefined;
}

export function createCourierProfile(userId: string, input: CourierProfileInput): Promise<void> {
  const user = users.find((u) => u.id === userId);
  const problem = checkProfile(input);
  if (!user || !user.roles.includes('COURIER')) return fail('Choose an account with the Courier role.');
  if ([...courierUserIds.values()].includes(userId)) return fail(`${user.name} already has a courier profile.`);
  if (problem) return fail(problem);
  const courierId = Math.max(...sampleCouriers.map((c) => c.courierId)) + 1;
  sampleCouriers.push({
    courierId, name: user.name, phoneNumber: input.phoneNumber.trim(),
    vehicleType: input.vehicleType.trim() || null, vehicleNumber: input.vehicleNumber.trim() || null, availability: input.availability,
  });
  courierUserIds.set(courierId, userId);
  changed();
  return delay(undefined);
}

export function updateCourierProfile(courierId: number, input: CourierProfileInput): Promise<void> {
  const courier = sampleCouriers.find((c) => c.courierId === courierId);
  const problem = checkProfile(input);
  if (!courier) return fail('This courier no longer exists.');
  if (problem) return fail(problem);
  Object.assign(courier, {
    phoneNumber: input.phoneNumber.trim(), vehicleType: input.vehicleType.trim() || null,
    vehicleNumber: input.vehicleNumber.trim() || null, availability: input.availability,
  });
  changed();
  return delay(undefined);
}

/* ---------- Delivery zones (no backend yet) ---------- */

export function listZones(): Promise<DeliveryZone[]> {
  return delay(zones);
}

export function saveZone(id: number | null, input: DeliveryZoneInput): Promise<void> {
  const name = input.name.trim();
  const code = input.code.trim().toUpperCase();
  if (!name) return fail('Enter a zone name.');
  if (!/^[A-Z]{2,4}$/.test(code)) return fail('The code is 2 to 4 letters, e.g. WP.');
  if (input.districts.length === 0) return fail('Choose at least one district.');
  if (zones.some((z) => z.id !== id && z.code === code)) return fail(`Another zone already uses the code ${code}.`);
  const taken = zones.filter((z) => z.id !== id).flatMap((z) => z.districts.filter((d) => input.districts.includes(d)).map((d) => `${d} (${z.code})`));
  if (taken.length) return fail(`Already in another zone: ${taken.join(', ')}.`);
  const row = { name, code, districts: allDistricts.filter((d) => input.districts.includes(d)), active: input.active };
  if (id === null) zones.push({ id: nextZoneId++, ...row });
  else {
    const zone = zones.find((z) => z.id === id);
    if (!zone) return fail('This zone no longer exists.');
    Object.assign(zone, row);
  }
  changed();
  return delay(undefined);
}

/* ---------- Pricing (CustomerPricingService) ---------- */

export function getPublishedPricing(): Promise<PublishedPricing> {
  return delay(pricing);
}

export function publishPricing(rates: PricingRates): Promise<PublishedPricing> {
  const values = Object.values(rates);
  if (values.some((v) => !Number.isFinite(v) || v < 0)) return fail('Rates must be zero or more.');
  if (values.some((v) => v > 1_000_000)) return fail('Rates must be under LKR 1,000,000.');
  pricing = { version: pricing.version + 1, publishedAt: new Date().toISOString(), rates: { ...rates } };
  changed();
  return delay(pricing);
}

const round2 = (n: number) => Math.round(n * 100) / 100;

/** The same formula as CustomerPricingService.estimate: district base + weight above 1 kg + service surcharge. */
export function quote(rates: PricingRates, request: QuoteRequest): Quote {
  const local = request.pickupDistrict.trim().toLowerCase() === request.destinationDistrict.trim().toLowerCase();
  const baseFee = local ? rates.sameDistrictBase : rates.otherDistrictBase;
  const weightCharge = round2(Math.max(0, request.totalWeightKg - 1) * rates.additionalKg);
  const serviceCharge = { STANDARD: 0, EXPRESS: rates.expressSurcharge, SAME_DAY: rates.sameDaySurcharge }[request.deliveryType];
  return { baseFee, weightCharge, serviceCharge, totalFee: round2(baseFee + weightCharge + serviceCharge) };
}

/* ---------- Reports (from the deliveries) ---------- */

export function dayKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/**
 * The last `days` days including today. Delivered counts by delivery date; failed attempts are FAILED tracking events
 * (a delivery can fail once and still be delivered later).
 */
export function getDeliveryReport(days: number): Promise<DeliveryReport> {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (days - 1));
  const inRange = (iso: string | null) => iso !== null && new Date(iso) >= start;

  const perDay = new Map<string, DayCount>();
  for (let i = 0; i < days; i += 1) {
    const date = new Date(start);
    date.setDate(start.getDate() + i);
    perDay.set(dayKey(date), { day: dayKey(date), delivered: 0, failedAttempts: 0 });
  }
  const couriers = new Map<number, CourierReport>(sampleCouriers.map((c) => [
    c.courierId, { courierId: c.courierId, name: c.name, delivered: 0, failedAttempts: 0, active: activeCount(c.courierId) },
  ]));

  let bookingsCreated = 0;
  let delivered = 0;
  let failedAttempts = 0;
  for (const d of sampleDeliveries) {
    if (inRange(d.createdAt)) bookingsCreated += 1;
    const courier = d.courierId === null ? undefined : couriers.get(d.courierId);
    if (d.status === 'DELIVERED' && d.deliveredAt && inRange(d.deliveredAt)) {
      delivered += 1;
      if (courier) courier.delivered += 1;
      const day = perDay.get(dayKey(new Date(d.deliveredAt)));
      if (day) day.delivered += 1;
    }
    for (const e of d.trackingEvents) {
      if (e.status !== 'FAILED' || !inRange(e.eventTime)) continue;
      failedAttempts += 1;
      if (courier) courier.failedAttempts += 1;
      const day = perDay.get(dayKey(new Date(e.eventTime)));
      if (day) day.failedAttempts += 1;
    }
  }
  return delay({
    bookingsCreated, delivered, failedAttempts, perDay: [...perDay.values()],
    couriers: [...couriers.values()].sort((a, b) => b.delivered - a.delivered || a.name.localeCompare(b.name)),
  });
}

/** Bookings created since midnight. */
export function countBookingsToday(): Promise<number> {
  const today = dayKey(new Date());
  return delay(sampleDeliveries.filter((d) => dayKey(new Date(d.createdAt)) === today).length);
}
