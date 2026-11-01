import { useEffect, useRef, useState } from 'react';
import { routes } from '../../../app/router';
import { AuthError, verifyEmail } from '../api/authApi';
import { AuthLayout } from '../components/AuthLayout';
import { Alert } from '../components/FormControls';

type State = { kind: 'working' } | { kind: 'done' } | { kind: 'failed'; message: string };

function tokenFromLink(): string | null {
  return new URLSearchParams(window.location.hash.slice(1)).get('token');
}

/** The link in the confirmation email opens /verify-email#token=... */
export function VerifyEmailPage() {
  const [token] = useState(tokenFromLink);
  const [state, setState] = useState<State>(() =>
    token ? { kind: 'working' } : { kind: 'failed', message: 'This link is incomplete. Open the link from the email again.' });
  const started = useRef(false); // React StrictMode runs effects twice in development: verify only once

  useEffect(() => {
    window.history.replaceState(null, '', window.location.pathname); // don't leave the token in the address bar
    if (!token || started.current) return;
    started.current = true;
    verifyEmail(token)
      .then(() => setState({ kind: 'done' }))
      .catch((failure) => setState({
        kind: 'failed',
        message: failure instanceof AuthError ? failure.message : 'Verification failed.',
      }));
  }, [token]);

  const title = state.kind === 'done' ? 'Email confirmed' : state.kind === 'failed' ? 'Link not valid' : 'Confirming…';
  return (
    <AuthLayout headline="Join SwiftDrop." tagline="Confirming your email address." eyebrow="VERIFY EMAIL"
      title={title} subtitle="">
      {state.kind === 'done' && <Alert kind="success">Your email is confirmed. You can log in now.</Alert>}
      {state.kind === 'failed' && <Alert kind="error">{state.message}</Alert>}
      {state.kind !== 'working' && <a href={routes.login}>Go to log in</a>}
    </AuthLayout>
  );
}
