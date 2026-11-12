import { useState, type FormEvent } from 'react';
import userIcon from '../../../assets/auth/icon-user.svg';
import { Alert, PrimaryButton, TextField } from '../../auth/components/FormControls';
import formStyles from '../../auth/components/FormControls.module.css';
import { ApiError, registerCustomer, type CustomerProfile, type RegisterCustomer } from '../api/customerApi';
import styles from './CustomerLayout.module.css';

interface CompleteProfileFormProps {
  /** The login account's name, if known: fills in the full name field until the customer edits it */
  suggestedName?: string | null;
  onRegistered: (profile: CustomerProfile) => void;
}

/**
 * Shown on the first visit: the account exists at the auth service, but SwiftDrop has no customer profile yet.
 * Asks for the customer's full name (shown on their dashboard) and phone. The email comes from the login account.
 */
export function CompleteProfileForm({ suggestedName, onRegistered }: CompleteProfileFormProps) {
  // null = not edited yet, so the suggested name (which may arrive after the form opens) is shown
  const [fullName, setFullName] = useState<string | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const shownName = fullName ?? suggestedName ?? '';

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    if (!shownName.trim()) {
      setFieldErrors({ fullName: 'Please enter your full name' });
      return;
    }
    setBusy(true);
    try {
      onRegistered(await registerCustomer({ fullName: shownName.trim(), phoneNumber }));
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
      <p className={styles.muted}>Welcome to SwiftDrop! Add your name and phone number to start sending parcels.</p>
      <form className={formStyles.form} onSubmit={onSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div>
          <TextField id="fullName" label="Full name" icon={userIcon} type="text" autoComplete="name" required
            maxLength={150} placeholder="Nimal Perera" value={shownName}
            onChange={(event) => setFullName(event.target.value)} />
          {fieldError('fullName')}
        </div>
        <div>
          <TextField id="phoneNumber" label="Phone number" icon={userIcon} type="tel" autoComplete="tel" required
            placeholder="0771234567" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} />
          {fieldError('phoneNumber')}
        </div>
        <PrimaryButton type="submit" disabled={busy}>{busy ? 'Saving…' : 'Continue to dashboard'}</PrimaryButton>
      </form>
    </section>
  );
}
