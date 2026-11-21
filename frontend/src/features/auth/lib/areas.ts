import { routes } from '../../../app/router';
import { dispatcherPath } from '../../dispatcher/dispatcherRoutes';
import { getAccessToken, readClaims } from './session';

export type AreaId = 'admin' | 'merchant' | 'customer';

export interface Area {
  id: AreaId;
  label: string;
  description: string;
  path: string;
}

/**
 * The parts of SwiftDrop one login may open ("hats"): staff can also be customers of their own parcels,
 * but a merchant account is a business, so it gets the merchant portal and never the customer screens.
 * Order = the main area first (where login lands). Display only: the backend checks every request.
 */
export function areasFor(entitlements: readonly string[]): Area[] {
  const areas: Area[] = [];
  const isMerchant = entitlements.includes('merchant:api-access');
  if (entitlements.includes('user:read')) {
    areas.push({ id: 'admin', label: 'Admin area', description: 'Users, merchants and dispatch', path: dispatcherPath('admin', false) });
  }
  if (isMerchant) {
    areas.push({ id: 'merchant', label: 'Merchant portal', description: 'Your business orders and settings', path: routes.merchantDashboard });
  }
  if (entitlements.includes('parcel:read-own') && !isMerchant) {
    areas.push({ id: 'customer', label: 'My parcels', description: 'Send and track your own parcels', path: routes.customerDashboard });
  }
  return areas;
}

/** Areas for the signed-in user (empty when signed out). */
export function currentAreas(): Area[] {
  const token = getAccessToken();
  if (!token) return [];
  try {
    return areasFor(readClaims(token).entitlements ?? []);
  } catch {
    return [];
  }
}

/** The other areas this user may switch to from `here`. */
export function otherAreas(here: AreaId): Area[] {
  return currentAreas().filter((area) => area.id !== here);
}
