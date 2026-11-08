import { useState, type FormEvent } from 'react';
import lockIcon from '../../../assets/auth/icon-lock.svg';
import mailIcon from '../../../assets/auth/icon-mail.svg';
import userIcon from '../../../assets/auth/icon-user.svg';
import { navigate, routes } from '../../../app/router';
import { AuthError, logIn, signUp, startGoogleLogin } from '../api/authApi';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, Divider, GoogleButton, PrimaryButton, TextField } from '../components/FormControls';
import styles from '../components/FormControls.module.css';

// The auth service's rule (NIST SP 800-63B): long passphrases beat complex short passwords
const MIN_PASSWORD_LENGTH = 15;

/** Figma "SwiftDrop desktop signup" / "mobile signup". */
export function SignupPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Use at least ${MIN_PASSWORD_LENGTH} characters, for example a few words: "blue river lamp cactus".`);
      return;
    }
    if (!fullName.trim()) {
      setError('Please enter your full name.');
      return;
    }
    setBusy(true);
    try {
      // The name is stored in the login account: every SwiftDrop module reads it from there (AppUser)
      await signUp(fullName.trim(), email, password);
      // Sign the new customer straight in and open their dashboard. If the auth service won't log them in yet
      // (for example the email still needs confirming), fall back to the "check your inbox" message.
      try {
        await logIn(email, password);
        navigate(routes.customerDashboard);
        return;
      } catch {
        setDone(true);
      }
    } catch (failure) {
      setError(failure instanceof AuthError ? failure.message : 'Cannot reach SwiftDrop. Check your connection.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      brandOnRight
      headline="Join SwiftDrop."
      tagline="Create an account to start shipping smarter today."
      eyebrow="SIGN UP"
      title="Create your account"
      subtitle="Book deliveries and track every parcel."
    >
      {done ? (
        // Same message whether or not the email was already registered (the service never reveals which)
        <Alert kind="success">Check your inbox: we sent a link to confirm {email}. Then log in.</Alert>
      ) : (
        <form className={styles.form} onSubmit={onSubmit} noValidate>
          {error && <Alert kind="error">{error}</Alert>}
          <TextField id="fullName" label="Full name" icon={userIcon} autoComplete="name" required
            placeholder="e.g. Isuru Perera" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          <TextField id="email" label="Email" icon={mailIcon} type="email" autoComplete="email" required
            placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <TextField id="password" label="Password" icon={lockIcon} type="password" autoComplete="new-password"
            required minLength={MIN_PASSWORD_LENGTH} placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
            value={password} onChange={(e) => setPassword(e.target.value)} />
          <p className={styles.hint}>
            By creating an account, you agree to SwiftDrop's{' '}
            <a href="https://getswiftdrop.me/terms.html" target="_blank" rel="noreferrer">Terms</a> and{' '}
            <a href="https://getswiftdrop.me/privacy.html" target="_blank" rel="noreferrer">Privacy Policy</a>.
          </p>
          <PrimaryButton type="submit" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</PrimaryButton>
        </form>
      )}

      <Divider>or continue with</Divider>
      <GoogleButton onClick={() => void startGoogleLogin()} />

      <p className={styles.footer}>
        Already have an account?{' '}
        <button type="button" className={styles.link} onClick={() => navigate(routes.login)}>Log in</button>
      </p>
    </AuthLayout>
  );
}
