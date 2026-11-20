import React, { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { DesktopSidebar } from './DesktopSidebar';
import { MobileBottomNav } from './MobileBottomNav';
import { env } from '../../config/env';
import { refresh } from '../../features/auth/api/authApi';
import { getAccessToken, readClaims } from '../../features/auth/lib/session';

export const CourierLayout: React.FC = () => {
  const [authChecked, setAuthChecked] = useState(env.isCourierMockMode);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    if (env.isCourierMockMode) {
      return;
    }

    let isMounted = true;
    (async () => {
      let token = getAccessToken();
      if (!token) {
        const refreshed = await refresh();
        if (!refreshed) {
          if (isMounted) {
            setAuthError('Authentication required. Please sign in with an authorized Courier account.');
            setAuthChecked(true);
          }
          return;
        }
        token = getAccessToken();
      }

      if (token) {
        try {
          const claims = readClaims(token);
          const roles = claims.roles || [];
          const entitlements = claims.entitlements || [];
          const isCourier = roles.includes('COURIER') || entitlements.includes('delivery:read-own');
          if (!isCourier) {
            if (isMounted) {
              setAuthError('Access Denied: Your account does not hold COURIER permissions.');
              setAuthChecked(true);
            }
            return;
          }
        } catch {
          // Token claims parsing fallback
        }
      }

      if (isMounted) {
        setAuthChecked(true);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  if (!authChecked) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 20px', color: '#746C65' }}>
        <p style={{ fontSize: '16px', fontWeight: 600 }}>Verifying Courier Authorization...</p>
      </div>
    );
  }

  if (authError) {
    return (
      <div
        style={{
          maxWidth: '480px',
          margin: '80px auto',
          padding: '32px',
          textAlign: 'center',
          backgroundColor: '#FFFFFF',
          borderRadius: '12px',
          border: '1px solid var(--border)',
          boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
        }}
      >
        <div style={{ fontSize: '32px', marginBottom: '12px' }}>🔒</div>
        <h2 style={{ fontSize: '18px', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '8px' }}>
          Courier Access Control
        </h2>
        <p style={{ color: '#DC2626', fontWeight: 600, fontSize: '14px', marginBottom: '20px', lineHeight: 1.5 }}>
          {authError}
        </p>
        <button
          type="button"
          className="btn-action primary"
          onClick={() => {
            window.location.href = '/';
          }}
          style={{ padding: '10px 24px', fontSize: '14px' }}
        >
          Return to Sign In
        </button>
      </div>
    );
  }

  return (
    <div className="app-container">
      <div className="dashboard-viewport">
        {/* Fixed Desktop Sidebar */}
        <DesktopSidebar />

        {/* Main Content Viewport */}
        <main className="dashboard-main">
          <Outlet />
        </main>
      </div>

      {/* Mobile Fixed Bottom Navigation */}
      <MobileBottomNav />
    </div>
  );
};
