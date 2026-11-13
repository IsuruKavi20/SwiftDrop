import { useEffect, useState } from 'react';
import { env } from '../../../config/env';
import { navigate, routes } from '../../../app/router';
import { authorizedFetch, logOut, refresh } from '../../auth/api/authApi';
import { getAccessToken, readClaims, type AccessClaims } from '../../auth/lib/session';
import { AuthLayout } from '../../auth/components/AuthLayout';
import { Alert } from '../../auth/components/FormControls';
import styles from '../../auth/components/FormControls.module.css';

/**
 * After login: shows who is signed in and calls the SwiftDrop backend with the token, proving the whole chain
 * (auth service issues the token, the backend checks it). Placeholder until the real dashboard exists.
 */
export function AccountPage() {
  const [claims, setClaims] = useState<AccessClaims | null>(null);
  const [backend, setBackend] = useState<string>('Checking the backend…');

  useEffect(() => {
    (async () => {
      if (!getAccessToken() && !(await refresh())) {
        navigate(routes.login);
        return;
      }
      const current = readClaims(getAccessToken()!);
      setClaims(current);
      // Ask for what this user's permissions allow: all deliveries (dispatcher/admin) or their own (courier)
      const permissions = current.entitlements ?? [];
      const [path, label] = permissions.includes('delivery:read-all') ? ['/api/deliveries', 'deliveries in total']
        : permissions.includes('delivery:read-own') ? ['/api/deliveries/mine', 'deliveries assigned to you']
          : [null, ''];
      if (!path) {
        setBackend('Signed in. Your role has no delivery access (customers book parcels instead).');
        return;
      }
      try {
        const response = await authorizedFetch(`${env.apiUrl}${path}`);
        setBackend(response.ok ? `Backend OK: ${(await response.json()).length} ${label}.`
          : `Backend answered ${response.status}.`);
      } catch {
        setBackend(`Backend not reachable at ${env.apiUrl} (is it running?).`);
      }
    })();
  }, []);

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  const permissions = claims?.entitlements ?? [];
  const roles = claims?.roles ?? [];
  const hasMerchant = permissions.includes('merchant:api-access') || roles.includes('MERCHANT');
  const hasCustomer = permissions.includes('parcel:read-own');

  return (
    <AuthLayout headline="Welcome back." tagline="You are signed in to SwiftDrop." eyebrow="ACCOUNT"
      title="You're logged in" subtitle={claims ? `User id ${claims.sub}` : ''}>
      {claims && (
        <>
          <p className={styles.hint}>Roles: {roles.join(', ') || 'none'}</p>
          <p className={styles.hint}>Permissions: {permissions.join(', ') || 'none'}</p>
          <Alert kind="success">{backend}</Alert>
          {hasMerchant && (
            <button
              type="button"
              className={styles.link}
              onClick={() => navigate(routes.merchantProfile)}
              style={{ display: 'block', margin: '12px 0 4px', fontWeight: 600 }}
            >
              Go to Merchant Profile →
            </button>
          )}
          {hasCustomer && (
            <button
              type="button"
              className={styles.link}
              onClick={() => navigate(routes.customerDashboard)}
              style={{ display: 'block', margin: '8px 0 4px', fontWeight: 600 }}
            >
              Go to Customer Dashboard →
            </button>
          )}
          <button type="button" className={styles.link} onClick={() => void onLogOut()}>Log out</button>
        </>
      )}
    </AuthLayout>
  );
}
