import { useEffect, useState, type ReactNode } from 'react';
import { navigate, routes } from '../../../app/router';
import { claimsWithFreshRoles } from '../../auth/lib/freshClaims';
import type { AccessClaims } from '../../auth/lib/session';
import styles from './MerchantLayout.module.css';

export function ProtectedMerchantRoute({ children }: { children: (claims: AccessClaims) => ReactNode }) {
  const [claims, setClaims] = useState<AccessClaims | null>(null);

  useEffect(() => {
    let active = true;

    void claimsWithFreshRoles('merchant:api-access').then((restoredClaims) => {
      if (!active) return;
      if (!restoredClaims) {
        navigate(routes.login, { replace: true });
      } else if (!restoredClaims.entitlements?.includes('merchant:api-access')) {
        navigate(routes.account, { replace: true });
      } else {
        setClaims(restoredClaims);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  return claims ? children(claims) : <p className={styles.loading} role="status">Checking your session…</p>;
}
