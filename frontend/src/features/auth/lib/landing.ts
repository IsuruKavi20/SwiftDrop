import { routes } from '../../../app/router';
import { getAccessToken, readClaims } from './session';

/**
 * Where a user goes right after logging in. Customers (permission "parcel:read-own") land on their dashboard;
 * every other role keeps the account page until its own home page exists. This only picks a page: the backend
 * still checks the permissions on every request.
 */
export function landingPage(): string {
  const token = getAccessToken();
  const permissions = token ? readClaims(token).entitlements ?? [] : [];
  return permissions.includes('parcel:read-own') ? routes.customerDashboard : routes.account;
}
