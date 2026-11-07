import { useState, type FormEvent } from 'react';
import mailIcon from '../../../assets/auth/icon-mail.svg';
import userIcon from '../../../assets/auth/icon-user.svg';
import { Alert, PrimaryButton, TextField } from '../../auth/components/FormControls';
import formStyles from '../../auth/components/FormControls.module.css';
import { ApiError, registerCustomer, type CustomerProfile, type RegisterCustomer } from '../api/customerApi';
import styles from './CustomerLayout.module.css';

/**
 * Shown on the first visit: the account exists at the auth service, but SwiftDrop has no customer profile yet.
 * A minimal registration form; the full Customer Registration page can replace it.
 */
export function CompleteProfileForm({ onRegistered }: { onRegistered: (profile: CustomerProfile) => void }) {
  const [details, setDetails] = useState<RegisterCustomer>({ firstName: '', lastName: '', email: '', phoneNumber: '' });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const update = (field: keyof RegisterCustomer) => (event: { target: { value: string } }) =>
    setDetails({ ...details, [field]: event.target.value });

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setBusy(true);
    try {
      onRegistered(await registerCustomer(details));
    } catch (failure) {
      if (failure instanceof ApiError) {
        setError(failure.message);
        setFieldErrors(failure.fieldErrors);
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  const fieldError = (field: keyof RegisterCustomer) =>
    fieldErrors[field] && <span className={styles.fieldError}>{fieldErrors[field]}</span>;

  return (
    <section className={styles.card}>
      <h2 className={styles.cardTitle}>Complete your profile</h2>
      <p className={styles.muted}>Welcome to SwiftDrop! Tell us a little about you to start sending parcels.</p>
      <form className={formStyles.form} onSubmit={onSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div className={styles.twoColumns}>
          <div>
            <TextField id="firstName" label="First name" icon={userIcon} autoComplete="given-name" required
              value={details.firstName} onChange={update('firstName')} />
            {fieldError('firstName')}
          </div>
          <div>
            <TextField id="lastName" label="Last name" icon={userIcon} autoComplete="family-name" required
              value={details.lastName} onChange={update('lastName')} />
            {fieldError('lastName')}
          </div>
        </div>
        <div>
          <TextField id="email" label="Email" icon={mailIcon} type="email" autoComplete="email" required
            placeholder="you@example.com" value={details.email} onChange={update('email')} />
          {fieldError('email')}
        </div>
        <div>
          <TextField id="phoneNumber" label="Phone number" icon={userIcon} type="tel" autoComplete="tel" required
            placeholder="0771234567" value={details.phoneNumber} onChange={update('phoneNumber')} />
          {fieldError('phoneNumber')}
        </div>
        <PrimaryButton type="submit" disabled={busy}>{busy ? 'Saving…' : 'Continue to dashboard'}</PrimaryButton>
      </form>
    </section>
  );
}
