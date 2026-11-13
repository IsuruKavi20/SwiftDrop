import { useState, type FormEvent } from 'react';
import userIcon from '../../../assets/auth/icon-user.svg';
import { Alert, PrimaryButton, TextField } from '../../auth/components/FormControls';
import formStyles from '../../auth/components/FormControls.module.css';
import {
  ApiError, registerCustomer, type AddressInput, type CustomerProfile, type RegisterCustomer,
} from '../api/customerApi';
import styles from './CustomerLayout.module.css';

const EMPTY_ADDRESS: AddressInput = { label: 'Home', street: '', city: '', district: '', postalCode: '' };

/**
 * Shown on the first visit: the account exists at the auth service, but SwiftDrop has no customer profile yet.
 * Name and email already come from the login account, so only the phone number and the first address are asked.
 * More addresses can be added later; each delivery can also use a different address.
 */
export function CompleteProfileForm({ onRegistered }: { onRegistered: (profile: CustomerProfile) => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [address, setAddress] = useState<AddressInput>(EMPTY_ADDRESS);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const updateAddress = (field: keyof AddressInput) => (event: { target: { value: string } }) =>
    setAddress({ ...address, [field]: event.target.value });

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setBusy(true);
    const details: RegisterCustomer = {
      phoneNumber,
      address: { ...address, postalCode: address.postalCode?.trim() || undefined },
    };
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

  // The backend names address fields "address.street" etc.
  const fieldError = (field: string) =>
    fieldErrors[field] && <span className={styles.fieldError}>{fieldErrors[field]}</span>;

  return (
    <section className={styles.card}>
      <h2 className={styles.cardTitle}>Complete your profile</h2>
      <p className={styles.muted}>
        Welcome to SwiftDrop! Add your phone number and your address to start sending parcels.
        You can add more addresses later.
      </p>
      <form className={formStyles.form} onSubmit={onSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div>
          <TextField id="phoneNumber" label="Phone number" icon={userIcon} type="tel" autoComplete="tel" required
            placeholder="0771234567" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} />
          {fieldError('phoneNumber')}
        </div>

        <h3 className={styles.cardTitle}>Your address</h3>
        {fieldError('address')}
        <div>
          <TextField id="addressLabel" label="Name for this address" icon={userIcon} required
            placeholder="Home" value={address.label} onChange={updateAddress('label')} />
          {fieldError('address.label')}
        </div>
        <div>
          <TextField id="street" label="Street" icon={userIcon} autoComplete="street-address" required
            placeholder="12 Galle Road" value={address.street} onChange={updateAddress('street')} />
          {fieldError('address.street')}
        </div>
        <div className={styles.twoColumns}>
          <div>
            <TextField id="city" label="City" icon={userIcon} autoComplete="address-level2" required
              placeholder="Colombo" value={address.city} onChange={updateAddress('city')} />
            {fieldError('address.city')}
          </div>
          <div>
            <TextField id="district" label="District" icon={userIcon} required
              placeholder="Colombo" value={address.district} onChange={updateAddress('district')} />
            {fieldError('address.district')}
          </div>
        </div>
        <div>
          <TextField id="postalCode" label="Postal code (optional)" icon={userIcon} autoComplete="postal-code"
            placeholder="00300" value={address.postalCode ?? ''} onChange={updateAddress('postalCode')} />
          {fieldError('address.postalCode')}
        </div>

        <PrimaryButton type="submit" disabled={busy}>{busy ? 'Saving…' : 'Continue to dashboard'}</PrimaryButton>
      </form>
    </section>
  );
}
