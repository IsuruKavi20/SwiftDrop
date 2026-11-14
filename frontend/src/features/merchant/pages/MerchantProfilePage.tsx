import { useCallback, useEffect, useState } from 'react';
import logoIcon from '../../../assets/auth/logo-box.svg';
import { navigate, routes } from '../../../app/router';
import { logOut, refresh } from '../../auth/api/authApi';
import { Alert } from '../../auth/components/FormControls';
import { getAccessToken, readClaims, type AccessClaims } from '../../auth/lib/session';
import { getMerchantProfile, MerchantApiError, type MerchantResponse } from '../api/merchantApi';
import { BusinessProfileForm } from '../components/BusinessProfileForm';
import styles from '../components/MerchantProfile.module.css';

type PageState =
  | { kind: 'loading' }
  | { kind: 'ready'; profile: MerchantResponse; claims: AccessClaims }
  | { kind: 'accessDenied'; message: string }
  | { kind: 'error'; message: string };

export function MerchantProfilePage() {
  const [state, setState] = useState<PageState>({ kind: 'loading' });

  const fetchProfile = useCallback(async () => {
    try {
      if (!getAccessToken() && !(await refresh())) {
        navigate(routes.login);
        return;
      }
      const token = getAccessToken()!;
      const claims = readClaims(token);
      const permissions = claims.entitlements ?? [];
      const hasMerchantAccess = permissions.includes('merchant:api-access');

      if (!hasMerchantAccess) {
        setState({
          kind: 'accessDenied',
          message:
            'You need merchant privileges to access this page. Please sign in with an authorized merchant account or contact your administrator.',
        });
        return;
      }

      const profile = await getMerchantProfile();
      setState({ kind: 'ready', profile, claims });
    } catch (err) {
      if (err instanceof MerchantApiError && err.status === 401) {
        navigate(routes.login);
      } else if (err instanceof MerchantApiError && err.status === 403) {
        setState({
          kind: 'accessDenied',
          message:
            'Access forbidden. Your account does not have merchant API permissions.',
        });
      } else {
        setState({
          kind: 'error',
          message:
            err instanceof Error ? err.message : 'Could not load your merchant profile.',
        });
      }
    }
  }, []);

  useEffect(() => {
    void (async () => {
      await fetchProfile();
    })();
  }, [fetchProfile]);

  function handleRetry() {
    setState({ kind: 'loading' });
    void fetchProfile();
  }

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  function handleProfileUpdated(updated: MerchantResponse) {
    setState((prev) => (prev.kind === 'ready' ? { ...prev, profile: updated } : prev));
  }

  const claims = state.kind === 'ready' ? state.claims : null;

  return (
    <div className={styles.page}>
      <header className={styles.navbar}>
        <a
          href={routes.merchantProfile}
          className={styles.navBrand}
          onClick={(e) => {
            e.preventDefault();
          }}
        >
          <span className={styles.logoMark}>
            <img src={logoIcon} width={20} height={20} alt="" />
          </span>
          SwiftDrop <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-muted)' }}>Merchant</span>
        </a>

        <div className={styles.navActions}>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate(routes.merchantDashboard)}
          >
            Dashboard
          </button>
          {claims?.entitlements?.includes('parcel:read-own') && (
            <button
              type="button"
              className={styles.ghostButton}
              onClick={() => navigate(routes.customerDashboard)}
            >
              Customer Dashboard
            </button>
          )}
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate(routes.account)}
          >
            Account
          </button>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => void onLogOut()}
          >
            Log out
          </button>
        </div>
      </header>

      <main className={styles.main}>
        {state.kind === 'loading' && (
          <div className={styles.card} style={{ textAlign: 'center', padding: '48px 24px' }}>
            <p className={styles.mutedText}>Loading merchant business profile…</p>
          </div>
        )}

        {state.kind === 'accessDenied' && (
          <div className={styles.card}>
            <h2 className={styles.title} style={{ marginBottom: '12px' }}>
              Access Denied
            </h2>
            <Alert kind="error">{state.message}</Alert>
            <div style={{ marginTop: '20px', display: 'flex', gap: '12px' }}>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={() => navigate(routes.account)}
              >
                Go to Account
              </button>
              <button
                type="button"
                className={styles.ghostButton}
                onClick={() => void onLogOut()}
              >
                Sign in with another account
              </button>
            </div>
          </div>
        )}

        {state.kind === 'error' && (
          <div className={styles.card}>
            <h2 className={styles.title} style={{ marginBottom: '12px' }}>
              Error Loading Profile
            </h2>
            <Alert kind="error">{state.message}</Alert>
            <div style={{ marginTop: '20px', display: 'flex', gap: '12px' }}>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={handleRetry}
              >
                Retry
              </button>
              <button
                type="button"
                className={styles.ghostButton}
                onClick={() => navigate(routes.account)}
              >
                Go to Account
              </button>
            </div>
          </div>
        )}

        {state.kind === 'ready' && (
          <BusinessProfileForm
            initialProfile={state.profile}
            onProfileUpdated={handleProfileUpdated}
          />
        )}
      </main>
    </div>
  );
}
