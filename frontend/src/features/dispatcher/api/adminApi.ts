import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { DISPATCH_CHANGED_EVENT, DispatchError, usingSampleData } from './dispatchCommon';
import { dispatcherRequest } from './dispatcherApi';
import type { CourierWorkload } from './dispatcherTypes';
import type {
  AdminRole,
  AdminUser,
  CourierProfile,
  CourierProfileInput,
  DeliveryReport,
  DeliveryZone,
  DeliveryZoneInput,
  MerchantApplication,
  MerchantApplicationStatus,
  PricingRates,
  PublishedPricing,
} from './adminTypes';
import * as sample from './sampleAdminApi';

/**
 * Everything the admin screens read and change goes through this file.
 * - Users and roles, merchant approvals: the SwiftDrop auth service (docs/auth-api/admin.md), called straight from
 *   the browser with the admin's own token, so auth records which admin did what.
 * - Courier profiles: the backend's dispatcher module (/api/dispatcher/couriers, docs/DISPATCHER-API.md).
 * - Pricing and delivery zones: the backend (/api/admin/pricing, /api/admin/zones, docs/PRICING-AND-ZONES-API.md).
 * - Reports and the overview's booking numbers: the backend (/api/admin/reports).
 * The development preview (not signed in) answers everything from sample data.
 */

export { allDistricts, dayKey, districtsByProvince, quote } from './sampleAdminApi';

const ADMIN_ROLES: readonly AdminRole[] = ['CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN'];
const MERCHANT_STATUSES: readonly MerchantApplicationStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'];
const MAX_REASON = 500;

function changed() {
  window.dispatchEvent(new Event(DISPATCH_CHANGED_EVENT));
}

/* ---------- Calling the auth service ---------- */

type Json = Record<string, unknown>;

class AuthNotFound extends DispatchError {}

async function authFailure(response: Response): Promise<never> {
  let detail: string | undefined;
  try {
    const problem = (await response.json()) as { detail?: string; title?: string; errors?: Record<string, string> };
    detail = Object.values(problem.errors ?? {})[0] ?? problem.detail ?? problem.title;
  } catch {
    // not JSON: use the generic sentence below
  }
  if (response.status === 401) detail = 'Your session has ended. Sign in again.';
  if (response.status === 403) detail = 'Your account is not allowed to do this. Sign out and in again if you were just made an admin.';
  const message = detail ?? 'The SwiftDrop auth service could not do this. Please try again.';
  throw response.status === 404 ? new AuthNotFound(message) : new DispatchError(message);
}

/** A call to `${env.authUrl}/api/v1/admin…` with the admin's token. Answers 204 (and empty bodies) with undefined. */
async function authAdmin<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.authUrl}/api/v1/admin${path}`, {
      ...init,
      headers: init.body ? { 'Content-Type': 'application/json', ...init.headers } : init.headers,
    });
  } catch {
    throw new DispatchError('Could not reach the SwiftDrop auth service. Check your connection and try again.');
  }
  if (!response.ok) return authFailure(response);
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/*
 * Reading auth's answers. Its docs list the calls but not every reply field, so these accept the usual spellings
 * (camelCase or snake_case, a few names) and lists either bare or wrapped. Confirm the real fields with the auth
 * team and trim these down.
 */

const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);

function text(raw: Json, ...keys: string[]): string | null {
  for (const key of keys) {
    const value = raw[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
    if (typeof value === 'number') return String(value);
  }
  return null;
}

function listIn(body: unknown): unknown[] | null {
  if (Array.isArray(body)) return body;
  if (!isObject(body)) return null;
  for (const key of ['content', 'items', 'users', 'merchants', 'results', 'Resources', 'data']) {
    if (Array.isArray(body[key])) return body[key] as unknown[];
  }
  return null;
}

function rolesOf(raw: Json): string[] {
  const roles = raw.roles;
  if (!Array.isArray(roles)) return [];
  return roles
    .map((role) => (typeof role === 'string' ? role : isObject(role) ? text(role, 'name', 'role', 'value') : null))
    .filter((role): role is string => !!role)
    .map((role) => role.toUpperCase().replace(/^ROLE_/, ''));
}

function emailOf(raw: Json): string {
  const direct = text(raw, 'email', 'emailAddress', 'email_address', 'primaryEmail', 'primary_email');
  if (direct) return direct;
  const emails = raw.emails;
  if (Array.isArray(emails) && isObject(emails[0])) return text(emails[0], 'value', 'email') ?? '';
  return '';
}

function activeOf(raw: Json): boolean {
  if (typeof raw.active === 'boolean') return raw.active;
  if (typeof raw.blocked === 'boolean') return !raw.blocked;
  if (typeof raw.enabled === 'boolean') return raw.enabled;
  const status = text(raw, 'status', 'state');
  return status ? status.toUpperCase() === 'ACTIVE' : true;
}

/** One person, with every role auth reports (DISPATCHER included). */
function personOf(raw: unknown): (AdminUser & { allRoles: string[] }) | null {
  if (!isObject(raw)) return null;
  const nested = isObject(raw.user) ? raw.user : raw;
  const id = text(nested, 'id', 'userId', 'user_id', 'sub');
  if (!id) return null;
  const email = emailOf(nested);
  const name = text(nested, 'name', 'displayName', 'display_name', 'fullName', 'full_name') ?? (email || 'Unnamed account');
  const allRoles = rolesOf(nested);
  return {
    id, name, email, active: activeOf(nested), allRoles,
    roles: allRoles.filter((role): role is AdminRole => (ADMIN_ROLES as readonly string[]).includes(role)),
  };
}

/** GET /admin/access-review: people listed directly, or grouped by role ({ ADMIN: [...] } or [{ role, users }]). */
function usersFromAccessReview(body: unknown): AdminUser[] {
  const people = new Map<string, AdminUser>();
  const add = (raw: unknown, role?: string) => {
    const person = personOf(raw);
    if (!person) return;
    const roles = new Set<AdminRole>([...(people.get(person.id)?.roles ?? []), ...person.roles]);
    const upper = role?.toUpperCase();
    if (upper && (ADMIN_ROLES as readonly string[]).includes(upper)) roles.add(upper as AdminRole);
    const known = people.get(person.id);
    people.set(person.id, {
      id: person.id,
      name: known && known.name !== 'Unnamed account' ? known.name : person.name,
      email: known?.email || person.email,
      active: known ? known.active && person.active : person.active,
      roles: ADMIN_ROLES.filter((r) => roles.has(r)),
    });
  };
  const readGroup = (item: unknown) => {
    if (!isObject(item)) return;
    const role = text(item, 'role', 'name');
    const members = ['users', 'members', 'holders', 'people', 'accounts'].map((key) => item[key]).find(Array.isArray) as unknown[] | undefined;
    if (role && members) members.forEach((member) => add(member, role));
    else add(item);
  };
  const readByRole = (grouped: Json) => {
    for (const [role, members] of Object.entries(grouped)) {
      if (Array.isArray(members)) members.forEach((member) => add(member, role));
    }
  };

  const list = listIn(body);
  if (list) list.forEach(readGroup);
  else if (isObject(body)) readByRole(isObject(body.roles) ? body.roles : body);
  return [...people.values()].sort((a, b) => a.name.localeCompare(b.name));
}

function merchantOf(raw: unknown, fallback: MerchantApplicationStatus): MerchantApplication | null {
  if (!isObject(raw)) return null;
  const merchantId = text(raw, 'merchantId', 'merchant_id', 'id');
  if (!merchantId) return null;
  const applicant = [raw.applicant, raw.owner, raw.user].find(isObject);
  const statusText = text(raw, 'status', 'state')?.toUpperCase();
  const status = statusText === 'ACTIVE' ? 'APPROVED'
    : (MERCHANT_STATUSES as readonly string[]).includes(statusText ?? '') ? statusText as MerchantApplicationStatus : fallback;
  return {
    merchantId,
    businessName: text(raw, 'businessName', 'business_name', 'name') ?? 'Unnamed business',
    registrationNumber: text(raw, 'registrationNumber', 'registration_number', 'registrationNo') ?? '',
    website: text(raw, 'website', 'websiteUrl', 'website_url'),
    applicantName: text(raw, 'applicantName', 'applicant_name', 'ownerName', 'owner_name', 'userName', 'user_name')
      ?? (applicant ? text(applicant, 'name', 'displayName') : null) ?? '',
    applicantEmail: text(raw, 'applicantEmail', 'applicant_email', 'ownerEmail', 'owner_email', 'email')
      ?? (applicant ? emailOf(applicant) : '') ?? '',
    submittedAt: text(raw, 'submittedAt', 'submitted_at', 'createdAt', 'created_at', 'appliedAt', 'applied_at') ?? '',
    status,
    decidedAt: text(raw, 'decidedAt', 'decided_at', 'reviewedAt', 'reviewed_at'),
    reason: text(raw, 'reason', 'rejectionReason', 'rejection_reason', 'decisionReason', 'decision_reason'),
  };
}

function checkReason(reason: string, required: boolean): string {
  const trimmed = reason.trim();
  if (required && !trimmed) throw new DispatchError('Give a reason; it is kept in the audit log.');
  if (trimmed.length > MAX_REASON) throw new DispatchError(`Keep the reason under ${MAX_REASON} characters.`);
  return trimmed;
}

/* ---------- Users and roles (auth service) ---------- */

/**
 * People who hold a role, from GET /admin/access-review. Auth has no "list every account" call; anyone else is found
 * by email (findUserByEmail).
 */
export async function listUsers(): Promise<AdminUser[]> {
  if (usingSampleData()) return sample.listUsers();
  return usersFromAccessReview(await authAdmin<unknown>('/access-review'));
}

/** GET /admin/users?email=… ; null when nobody has that email. */
export async function findUserByEmail(email: string): Promise<AdminUser | null> {
  const wanted = email.trim().toLowerCase();
  if (usingSampleData()) return (await sample.listUsers()).find((u) => u.email.toLowerCase() === wanted) ?? null;
  try {
    const body = await authAdmin<unknown>(`/users?email=${encodeURIComponent(email.trim())}`);
    const list = listIn(body);
    return personOf(list ? list[0] : body);
  } catch (failure) {
    if (failure instanceof AuthNotFound) return null;
    throw failure;
  }
}

async function getPerson(userId: string) {
  const person = personOf(await authAdmin<unknown>(`/users/${encodeURIComponent(userId)}`));
  if (!person) throw new DispatchError('This account no longer exists.');
  return person;
}

/**
 * PUT / DELETE /admin/users/{userId}/roles/{role} for each role that changed: added first, then removed, so the person
 * always keeps a role. Roles the admin area doesn't offer (DISPATCHER) are left alone. Taking Courier away from someone
 * still carrying deliveries is refused here; reassign them first.
 */
export async function setUserRoles(userId: string, roles: AdminRole[]): Promise<void> {
  if (usingSampleData()) return sample.setUserRoles(userId, roles);
  if (roles.length === 0) throw new DispatchError('Keep at least one role.');
  const person = await getPerson(userId);
  const adding = roles.filter((role) => !person.allRoles.includes(role));
  const removing = ADMIN_ROLES.filter((role) => person.allRoles.includes(role) && !roles.includes(role));

  if (removing.includes('COURIER')) {
    const profile = (await dispatcherRequest<CourierWorkload[]>('/couriers')).find((c) => c.userId === userId);
    if (profile && profile.activeDeliveries > 0) {
      const n = profile.activeDeliveries;
      throw new DispatchError(`${person.name} still carries ${n} active ${n === 1 ? 'delivery' : 'deliveries'}. Reassign them first.`);
    }
  }
  try {
    for (const role of adding) await authAdmin<void>(`/users/${encodeURIComponent(userId)}/roles/${role}`, { method: 'PUT' });
    for (const role of removing) await authAdmin<void>(`/users/${encodeURIComponent(userId)}/roles/${role}`, { method: 'DELETE' });
  } finally {
    // Some roles may have changed before a refusal: show what auth holds now
    changed();
  }
}

/** POST /admin/users/{userId}/block or /unblock with { reason } (kept in auth's audit log). */
export async function setUserBlocked(userId: string, blocked: boolean, reason: string): Promise<void> {
  if (usingSampleData()) return sample.setUserBlocked(userId, blocked, reason);
  const trimmed = checkReason(reason, true);
  await authAdmin<void>(`/users/${encodeURIComponent(userId)}/${blocked ? 'block' : 'unblock'}`, {
    method: 'POST',
    body: JSON.stringify({ reason: trimmed }),
  });
  changed();
}

/* ---------- Merchant approvals (auth service) ---------- */

async function merchantsWithStatus(status: MerchantApplicationStatus): Promise<MerchantApplication[]> {
  const body = await authAdmin<unknown>(`/merchants?status=${status}`);
  return (listIn(body) ?? [])
    .map((raw) => merchantOf(raw, status))
    .filter((m): m is MerchantApplication => m !== null);
}

/** GET /admin/merchants?status=… for one status, or every status; newest first. */
export async function listMerchantApplications(status?: MerchantApplicationStatus): Promise<MerchantApplication[]> {
  if (usingSampleData()) return sample.listMerchantApplications(status);
  let rows: MerchantApplication[];
  if (status) {
    rows = await merchantsWithStatus(status);
  } else {
    const answers = await Promise.allSettled(MERCHANT_STATUSES.map(merchantsWithStatus));
    const failed = answers.find((a): a is PromiseRejectedResult => a.status === 'rejected');
    if (failed && answers.every((a) => a.status === 'rejected')) throw failed.reason;
    const byId = new Map<string, MerchantApplication>();
    answers.forEach((a) => a.status === 'fulfilled' && a.value.forEach((m) => byId.set(m.merchantId, m)));
    rows = [...byId.values()];
  }
  return rows.sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));
}

/** POST /admin/merchants/{merchantId}/decision { decision, reason }. Approval gives the MERCHANT role. */
export async function decideMerchant(merchantId: string, decision: 'APPROVE' | 'REJECT', reason = ''): Promise<void> {
  if (usingSampleData()) return sample.decideMerchant(merchantId, decision, reason);
  const trimmed = checkReason(reason, false);
  if (decision === 'REJECT' && !trimmed) throw new DispatchError('Tell the applicant why it was rejected.');
  await authAdmin<void>(`/merchants/${encodeURIComponent(merchantId)}/decision`, {
    method: 'POST',
    body: JSON.stringify(trimmed ? { decision, reason: trimmed } : { decision }),
  });
  changed();
}

/** POST /admin/merchants/{merchantId}/status { change: SUSPEND | REINSTATE }. */
export async function setMerchantSuspended(merchantId: string, suspended: boolean): Promise<void> {
  if (usingSampleData()) return sample.setMerchantSuspended(merchantId, suspended);
  await authAdmin<void>(`/merchants/${encodeURIComponent(merchantId)}/status`, {
    method: 'POST',
    body: JSON.stringify({ change: suspended ? 'SUSPEND' : 'REINSTATE' }),
  });
  changed();
}

/* ---------- Courier profiles (backend dispatcher module) ---------- */

/** Emails are read live from auth (GET /admin/users/{id}), never stored; empty when one can't be read. */
async function emailsOf(userIds: string[]): Promise<Map<string, string>> {
  const answers = await Promise.allSettled(userIds.map((id) => getPerson(id)));
  const emails = new Map<string, string>();
  answers.forEach((answer, i) => emails.set(userIds[i], answer.status === 'fulfilled' ? answer.value.email : ''));
  return emails;
}

/** Every courier profile with its workload (GET /api/dispatcher/couriers), by name. */
export async function listCourierProfiles(): Promise<CourierProfile[]> {
  if (usingSampleData()) return sample.listCourierProfiles();
  const couriers = await dispatcherRequest<CourierWorkload[]>('/couriers');
  const emails = await emailsOf(couriers.map((c) => c.userId ?? '').filter(Boolean));
  return couriers
    .map((c) => ({
      courierId: c.courierId,
      userId: c.userId ?? '',
      name: c.name,
      email: emails.get(c.userId ?? '') ?? '',
      phoneNumber: c.phoneNumber,
      vehicleType: c.vehicleType,
      vehicleNumber: c.vehicleNumber,
      availability: c.availability,
      activeDeliveries: c.activeDeliveries,
      accountActive: c.accountActive ?? true,
      courierRole: c.courierRole ?? true,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** People with the Courier role who have no work profile yet (GET /api/dispatcher/couriers/candidates). */
export async function listCourierAccountsWithoutProfile(): Promise<AdminUser[]> {
  if (usingSampleData()) return sample.listCourierAccountsWithoutProfile();
  const candidates = await dispatcherRequest<{ userId: string; name: string }[]>('/couriers/candidates');
  return candidates.map((c) => ({ id: c.userId, name: c.name, email: '', roles: ['COURIER'], active: true }));
}

function profileBody(input: CourierProfileInput, userId?: string): string {
  return JSON.stringify({
    userId,
    phoneNumber: input.phoneNumber.trim(),
    vehicleType: input.vehicleType.trim() || null,
    vehicleNumber: input.vehicleNumber.trim() || null,
    availability: input.availability,
  });
}

/** POST /api/dispatcher/couriers: only for an active account with the Courier role, one profile each. */
export async function createCourierProfile(userId: string, input: CourierProfileInput): Promise<void> {
  if (usingSampleData()) return sample.createCourierProfile(userId, input);
  await dispatcherRequest('/couriers', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: profileBody(input, userId) });
  changed();
}

/** PUT /api/dispatcher/couriers/{courierId}: phone, vehicle, availability. */
export async function updateCourierProfile(courierId: number, input: CourierProfileInput): Promise<void> {
  if (usingSampleData()) return sample.updateCourierProfile(courierId, input);
  await dispatcherRequest(`/couriers/${courierId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: profileBody(input) });
  changed();
}

/* ---------- Pricing and delivery zones (backend) ---------- */

/** A call to the backend's /api/admin endpoints with the admin's token; problems become a DispatchError. */
async function backendAdmin<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.apiUrl}/api/admin${path}`, {
      ...init,
      headers: init.body ? { 'Content-Type': 'application/json', ...init.headers } : init.headers,
    });
  } catch {
    throw new DispatchError('Could not reach the SwiftDrop server. Check your connection and try again.');
  }
  if (!response.ok) {
    let detail: string | undefined;
    try {
      const problem = (await response.json()) as { detail?: string; errors?: Record<string, string> };
      detail = Object.values(problem.errors ?? {})[0] ?? problem.detail;
    } catch {
      // not JSON: use the generic sentence below
    }
    if (response.status === 401) detail = 'Your session has ended. Sign in again.';
    if (response.status === 403) detail = 'Only admins can use the admin screens.';
    throw new DispatchError(detail ?? 'Something went wrong. Please try again.');
  }
  return (await response.json()) as T;
}

/** The tariff quotes use now (GET /api/admin/pricing); version 0 while nothing is published. */
export async function getPublishedPricing(): Promise<PublishedPricing> {
  if (usingSampleData()) return sample.getPublishedPricing();
  return backendAdmin<PublishedPricing>('/pricing');
}

/** POST /api/admin/pricing: the next version. New quotes use it at once; booked parcels keep their fee. */
export async function publishPricing(rates: PricingRates): Promise<PublishedPricing> {
  if (usingSampleData()) return sample.publishPricing(rates);
  const published = await backendAdmin<PublishedPricing>('/pricing', { method: 'POST', body: JSON.stringify(rates) });
  changed();
  return published;
}

/** Every delivery zone by name (GET /api/admin/zones). */
export async function listZones(): Promise<DeliveryZone[]> {
  if (usingSampleData()) return sample.listZones();
  return backendAdmin<DeliveryZone[]>('/zones');
}

/** POST /api/admin/zones (id null) or PUT /api/admin/zones/{id}. Each district may be in one zone only. */
export async function saveZone(id: number | null, input: DeliveryZoneInput): Promise<void> {
  if (usingSampleData()) return sample.saveZone(id, input);
  const body = JSON.stringify({ name: input.name.trim(), code: input.code.trim(), districts: input.districts, active: input.active });
  await backendAdmin<DeliveryZone>(id === null ? '/zones' : `/zones/${id}`, { method: id === null ? 'POST' : 'PUT', body });
  changed();
}

/* ---------- Reports (backend) ---------- */

/**
 * The last `days` Sri Lanka days, today included (1 to 90): bookings, deliveries by delivery date, failed attempts
 * (FAILED tracking events), per day and per courier.
 */
export async function getDeliveryReport(days: number): Promise<DeliveryReport> {
  if (usingSampleData()) return sample.getDeliveryReport(days);
  return backendAdmin<DeliveryReport>(`/reports?days=${days}`);
}

/** Bookings created since midnight, Sri Lanka time. */
export async function countBookingsToday(): Promise<number> {
  if (usingSampleData()) return sample.countBookingsToday();
  return (await getDeliveryReport(1)).bookingsCreated;
}
