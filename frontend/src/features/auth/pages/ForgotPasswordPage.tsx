import { useState, type FormEvent } from 'react';
import mailIcon from '../../../assets/auth/icon-mail.svg';
import { navigate, routes } from '../../../app/router';
import { AuthError, requestPasswordReset } from '../api/authApi';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, PrimaryButton, TextField } from '../components/FormControls';
import styles from '../components/FormControls.module.css';

/**
 * "Forgot password?": asks the auth service to email a reset link. The answer is the same whether or not the email
 * has an account, so this page never tells anyone which emails are registered.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!email.trim()) {
      setError('Please enter your email address.');
      return;
    }
    setBusy(true);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch (failure) {
      setError(failure instanceof AuthError && failure.status === 429
        ? 'Too many requests. Please try again in a minute.'
        : 'Cannot reach SwiftDrop. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout headline="Forgot your password?" tagline="It happens. We'll send you a link to choose a new one."
      eyebrow="RESET PASSWORD" title="Reset your password"
      subtitle={sent ? '' : 'Enter the email you use for SwiftDrop.'}>
      {sent ? (
        <Alert kind="success">
          If an account exists for this email, we sent a link. Check your inbox: the link works once, for 30 minutes.
        </Alert>
      ) : (
        <form className={styles.form} onSubmit={onSubmit} noValidate>
          {error && <Alert kind="error">{error}</Alert>}
          <TextField id="email" label="Email" icon={mailIcon} type="email" autoComplete="email" required
            placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <PrimaryButton type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</PrimaryButton>
        </form>
      )}
      <p className={styles.forgot}>
        <a href={routes.login} onClick={(e) => { e.preventDefault(); navigate(routes.login); }}>Back to log in</a>
      </p>
    </AuthLayout>
  );
}
