import { routes } from '../../../app/router';
import { getAccessToken, readClaims } from './session';

/**
 * Where a user goes right after logging in.
 * Users with "merchant:api-access" land on the merchant dashboard.
 * Customers (permission "parcel:read-own") land on their dashboard.
 * Every other role keeps the account page until its own home page exists. This only picks a page:
 * the backend still checks the permissions on every request.
 */
export function landingPage(): string {
  const token = getAccessToken();
  if (!token) return routes.login;

  const claims = readClaims(token);
  const permissions = claims.entitlements ?? [];

  if (permissions.includes('merchant:api-access')) {
    return routes.merchantDashboard;
  }
  if (permissions.includes('parcel:read-own')) {
    return routes.customerDashboard;
  }
  return routes.account;
}
