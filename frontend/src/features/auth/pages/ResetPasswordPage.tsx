import { useEffect, useState, type FormEvent } from 'react';
import lockIcon from '../../../assets/auth/icon-lock.svg';
import { navigate, routes } from '../../../app/router';
import { AuthError, confirmPasswordReset } from '../api/authApi';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, PrimaryButton, TextField } from '../components/FormControls';
import styles from '../components/FormControls.module.css';

const MIN_PASSWORD_LENGTH = 15;

function tokenFromLink(): string | null {
  return new URLSearchParams(window.location.hash.slice(1)).get('token');
}

/**
 * The link in the reset email opens /reset-password#token=... The token is in the fragment (never sent to servers).
 * Nothing is sent on page load (email scanners open links): only when the person presses the button.
 */
export function ResetPasswordPage() {
  const [token] = useState(tokenFromLink);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(token ? null : 'This link is incomplete. Open the link from the email again.');
  const [linkDead, setLinkDead] = useState(!token);

  useEffect(() => {
    window.history.replaceState(null, '', window.location.pathname); // don't leave the token in the address bar
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!token) return;
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Use at least ${MIN_PASSWORD_LENGTH} characters, for example a few words: "blue river lamp cactus".`);
      return;
    }
    setBusy(true);
    try {
      await confirmPasswordReset(token, password);
      setDone(true);
    } catch (failure) {
      if (failure instanceof AuthError && failure.code === 'INVALID_OR_EXPIRED_TOKEN') {
        setLinkDead(true);
        setError('This link has expired or was already used. Ask for a new one.');
      } else if (failure instanceof AuthError && failure.status === 400) {
        setError(failure.message); // e.g. WEAK_PASSWORD: the link still works, try another password
      } else {
        setError('Cannot reach SwiftDrop. Check your connection and try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  const goTo = (path: string) => (e: { preventDefault(): void }) => { e.preventDefault(); navigate(path); };

  return (
    <AuthLayout headline="Choose a new password." tagline="Pick something long and easy for you to remember."
      eyebrow="RESET PASSWORD" title={done ? 'Password changed' : 'Choose a new password'} subtitle="">
      {done && (
        <>
          <Alert kind="success">Your password is changed and you were signed out everywhere. Log in with the new one.</Alert>
          <PrimaryButton type="button" onClick={() => navigate(routes.login)}>Go to log in</PrimaryButton>
        </>
      )}
      {!done && linkDead && (
        <>
          {error && <Alert kind="error">{error}</Alert>}
          <p className={styles.forgot}>
            <a href={routes.forgotPassword} onClick={goTo(routes.forgotPassword)}>Send me a new link</a>
          </p>
        </>
      )}
      {!done && !linkDead && (
        <form className={styles.form} onSubmit={onSubmit} noValidate>
          {error && <Alert kind="error">{error}</Alert>}
          <TextField id="password" label="New password" icon={lockIcon} type="password" autoComplete="new-password"
            required placeholder="At least 15 characters" value={password} onChange={(e) => setPassword(e.target.value)} />
          <PrimaryButton type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save new password'}</PrimaryButton>
        </form>
      )}
    </AuthLayout>
  );
}
