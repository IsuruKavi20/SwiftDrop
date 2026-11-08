import { useEffect, useRef, useState } from 'react';
import { navigate, routes } from '../../../app/router';
import { AuthError, finishGoogleLogin } from '../api/authApi';
import { landingPage } from '../lib/landing';
import { AuthLayout } from '../components/AuthLayout';
import { Alert } from '../components/FormControls';

function codeFromRedirect(): string | null {
  return new URLSearchParams(window.location.hash.slice(1)).get('code');
}

/**
 * Google login returns here as /auth/callback#code=... (the code is in the #fragment, so it never reaches any
 * server log). We exchange it, together with this tab's secret, for tokens.
 */
export function GoogleCallbackPage() {
  const [code] = useState(codeFromRedirect);
  const [error, setError] = useState<string | null>(() =>
    code ? null : 'Google login was cancelled or failed. Please try again.');
  const started = useRef(false); // React StrictMode runs effects twice in development: exchange only once

  useEffect(() => {
    window.history.replaceState(null, '', window.location.pathname); // don't leave the code in the address bar
    if (!code || started.current) return;
    started.current = true;
    finishGoogleLogin(code)
      .then(() => navigate(landingPage()))
      .catch((failure) => setError(failure instanceof AuthError ? failure.message : 'Google login failed.'));
  }, [code]);

  return (
    <AuthLayout headline="Welcome back." tagline="Signing you in with Google." eyebrow="GOOGLE"
      title={error ? 'Login failed' : 'Signing you in…'} subtitle={error ? '' : 'One moment.'}>
      {error && (
        <>
          <Alert kind="error">{error}</Alert>
          <a href={routes.login}>Back to log in</a>
        </>
      )}
    </AuthLayout>
  );
}
