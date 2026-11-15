import { useEffect, useState, type ReactNode } from 'react';
import { navigate, routes } from '../../../app/router';
import { claimsWithFreshRoles } from '../../auth/lib/freshClaims';
import type { AccessClaims } from '../../auth/lib/session';
import styles from './Dispatcher.module.css';

/**
 * Only ADMIN has user:read (DISPATCHER has parcel:assign too, but SwiftDrop gives the admin area to admins).
 * The backend and the auth service still check every call.
 */
const DISPATCH_ENTITLEMENT = 'user:read';

export function ProtectedDispatcherRoute({ children }: { children: (claims: AccessClaims) => ReactNode }) {
  const [claims, setClaims] = useState<AccessClaims | null>(null);

  useEffect(() => {
    let active = true;
    void claimsWithFreshRoles(DISPATCH_ENTITLEMENT).then((restored) => {
      if (!active) return;
      if (!restored) navigate(routes.login, { replace: true });
      else if (!restored.entitlements?.includes(DISPATCH_ENTITLEMENT)) navigate(routes.account, { replace: true });
      else setClaims(restored);
    });
    return () => {
      active = false;
    };
  }, []);

  return claims ? children(claims) : <p className={styles.loadingPage} role="status">Checking your session…</p>;
}
