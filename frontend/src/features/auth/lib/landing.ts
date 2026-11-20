import { routes } from '../../../app/router';
import { dispatcherPath } from '../../dispatcher/dispatcherRoutes';
import { getAccessToken, readClaims } from './session';

const NEXT_KEY = 'swiftdrop.next';
/** A merchant sign-up often finishes in another tab (the confirm-email link), so the wish must outlive the tab. */
const NEXT_LIFETIME_MS = 24 * 60 * 60 * 1000;

/**
 * Pages a login may continue to ("?next=..."). Exact matches only: an address from the URL is never followed
 * otherwise, so nobody can send users to another site after logging in (open redirect).
 */
const ALLOWED_NEXT: readonly string[] = [routes.merchantApply];

interface RememberedNext {
  page: string;
  until: number;
}

/**
 * Remembers "?next=" from the current address for after the login. Kept in localStorage (all tabs) for 24 hours,
 * so it survives the Google login round trip AND the confirm-email link that opens in a new tab.
 * Call it when the login and sign-up pages open.
 */
export function rememberNextFromUrl(): void {
  const next = new URLSearchParams(window.location.search).get('next');
  if (!next || !ALLOWED_NEXT.includes(next)) return;
  const remembered: RememberedNext = { page: next, until: Date.now() + NEXT_LIFETIME_MS };
  try {
    localStorage.setItem(NEXT_KEY, JSON.stringify(remembered));
  } catch {
    // Storage blocked (private mode): the login still works, it just lands on the normal home page
  }
}

/** Forget a remembered page: plain "Log in" / "Sign up" means the user no longer wants that page. */
export function forgetNext(): void {
  try {
    localStorage.removeItem(NEXT_KEY);
  } catch {
    // Nothing stored or storage blocked: nothing to forget
  }
}

/** Reads the remembered page once (then forgets it). Expired, broken or unknown values are ignored. */
function takeNext(): string | null {
  let raw: string | null;
  try {
    raw = localStorage.getItem(NEXT_KEY);
  } catch {
    return null;
  }
  forgetNext();
  if (!raw) return null;
  try {
    const remembered = JSON.parse(raw) as Partial<RememberedNext>;
    const page = remembered.page;
    const valid = typeof page === 'string' && ALLOWED_NEXT.includes(page)
      && typeof remembered.until === 'number' && remembered.until > Date.now();
    return valid ? page : null;
  } catch {
    return null;
  }
}

/** The address of a page that keeps "?next=" (e.g. the sign-up link from the "sell with SwiftDrop" link). */
export function withNext(page: string, next: string): string {
  return `${page}?next=${encodeURIComponent(next)}`;
}

/**
 * Where a user goes right after logging in: a remembered allowed page first (e.g. the merchant application, or the
 * merchant dashboard when they are already a merchant);
 * otherwise admins ("user:read") land on the admin overview (/admin), users with "merchant:api-access" land on the
 * merchant dashboard, customers (permission "parcel:read-own")
 * land on their dashboard, and every other role keeps the account page until its own home page exists.
 * This only picks a page: the backend still checks permissions on every request.
 */
export function landingPage(): string {
  const token = getAccessToken();
  if (!token) return routes.login; // keep any remembered page for the real login
  const next = takeNext();

  const claims = readClaims(token);
  const permissions = claims.entitlements ?? [];

  // The merchant application is only for plain customers: admins and existing merchants ignore an old
  // "Sell with SwiftDrop" click (e.g. from testing) and go to their own area below
  const canApply = !permissions.includes('user:read') && !permissions.includes('merchant:api-access');
  if (next && (next !== routes.merchantApply || canApply)) return next;

  // Admins first: their token can also carry customer permissions, and "user:read" is what the admin area checks.
  if (permissions.includes('user:read')) {
    return dispatcherPath('admin', false);
  }
  if (permissions.includes('merchant:api-access')) {
    return routes.merchantDashboard;
  }
  if (permissions.includes('parcel:read-own')) {
    return routes.customerDashboard;
  }
  return routes.account;
}
