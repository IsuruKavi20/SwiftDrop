import { routes } from '../../../app/router';
import { getAccessToken, readClaims } from './session';

const NEXT_KEY = 'swiftdrop.next';

/**
 * Pages a login may continue to ("?next=..."). Exact matches only: an address from the URL is never followed
 * otherwise, so nobody can send users to another site after logging in (open redirect).
 */
const ALLOWED_NEXT: readonly string[] = [routes.merchantApply];

/**
 * Remembers "?next=" from the current address for after the login. Kept in sessionStorage so it survives the
 * Google login round trip. Call it when the login and sign-up pages open.
 */
export function rememberNextFromUrl(): void {
  const next = new URLSearchParams(window.location.search).get('next');
  if (next && ALLOWED_NEXT.includes(next)) {
    sessionStorage.setItem(NEXT_KEY, next);
  }
}

/** The address of a page that keeps "?next=" (e.g. the sign-up link from the "sell with SwiftDrop" link). */
export function withNext(page: string, next: string): string {
  return `${page}?next=${encodeURIComponent(next)}`;
}

/**
 * Where a user goes right after logging in: a remembered allowed page first (e.g. the merchant application);
 * otherwise customers (permission "parcel:read-own") land on their dashboard and every other role keeps the account
 * page until its own home page exists. This only picks a page: the backend still checks permissions on every request.
 */
export function landingPage(): string {
  const next = sessionStorage.getItem(NEXT_KEY);
  sessionStorage.removeItem(NEXT_KEY);
  if (next && ALLOWED_NEXT.includes(next)) {
    return next;
  }
  const token = getAccessToken();
  const permissions = token ? readClaims(token).entitlements ?? [] : [];
  return permissions.includes('parcel:read-own') ? routes.customerDashboard : routes.account;
}
