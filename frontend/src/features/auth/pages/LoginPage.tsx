import { useEffect, useState, type FormEvent } from 'react';
import lockIcon from '../../../assets/auth/icon-lock.svg';
import mailIcon from '../../../assets/auth/icon-mail.svg';
import { navigate, routes } from '../../../app/router';
import { AuthError, logIn, startGoogleLogin } from '../api/authApi';
import { landingPage, rememberNextFromUrl, withNext } from '../lib/landing';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, Divider, GoogleButton, PrimaryButton, TextField } from '../components/FormControls';
import styles from '../components/FormControls.module.css';

/** Figma "SwiftDrop desktop login" / "mobile login". */
export function LoginPage() {
  const [email, setEmail] = useState('');
  // "?next=" (e.g. from "Sell with SwiftDrop") is remembered for after the login, also across Google login
  useEffect(rememberNextFromUrl, []);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await logIn(email, password);
      navigate(landingPage());
    } catch (failure) {
      // The service answers wrong email and wrong password the same way (no hint which one was wrong)
      setError(failure instanceof AuthError ? failure.message : 'Cannot reach SwiftDrop. Check your connection.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      headline="Welcome back."
      tagline="Your parcels are waiting for you. Log in to track, manage and deliver."
      eyebrow="LOG IN"
      title="Log in to your account"
      subtitle="Enter your details to continue."
    >
      <form className={styles.form} onSubmit={onSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <TextField id="email" label="Email" icon={mailIcon} type="email" autoComplete="email" required
          placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField id="password" label="Password" icon={lockIcon} type="password" autoComplete="current-password"
          required placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
        <p className={styles.forgot} title="Password reset is coming soon">Forgot password? (coming soon)</p>
        <PrimaryButton type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</PrimaryButton>
      </form>

      <Divider>or continue with</Divider>
      <GoogleButton onClick={() => void startGoogleLogin()} />

      <p className={styles.footer}>
        New here?{' '}
        <button type="button" className={styles.link} onClick={() => navigate(routes.signup)}>Create an account</button>
      </p>
      <p className={styles.footer}>
        Are you a business?{' '}
        <button type="button" className={styles.link}
          onClick={() => navigate(withNext(routes.signup, routes.merchantApply))}>
          Sell with SwiftDrop
        </button>
      </p>
    </AuthLayout>
  );
}
