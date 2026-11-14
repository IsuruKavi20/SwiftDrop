import { useEffect, useState, type ReactNode } from 'react';
import { navigate, routes } from '../../../app/router';
import { refresh } from '../../auth/api/authApi';
import { getAccessToken, readClaims, type AccessClaims } from '../../auth/lib/session';
import styles from './MerchantLayout.module.css';

let pendingRefresh: Promise<boolean> | undefined;

function currentClaims(): AccessClaims | null {
  const token = getAccessToken();
  if (!token) return null;

  try {
    const claims = readClaims(token);
    return claims.exp > Date.now() / 1000 ? claims : null;
  } catch {
    return null;
  }
}

async function restoreClaims(): Promise<AccessClaims | null> {
  const claims = currentClaims();
  if (claims) return claims;

  // Share the existing refresh call across Strict Mode effect replays and quick route re-entry.
  pendingRefresh ??= refresh().catch(() => false).finally(() => {
    pendingRefresh = undefined;
  });
  return (await pendingRefresh) ? currentClaims() : null;
}

export function ProtectedMerchantRoute({ children }: { children: (claims: AccessClaims) => ReactNode }) {
  const [claims, setClaims] = useState<AccessClaims | null>(null);

  useEffect(() => {
    let active = true;

    void restoreClaims().then((restoredClaims) => {
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
