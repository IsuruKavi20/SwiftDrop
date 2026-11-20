import { useEffect, useRef, useState, type FormEvent } from 'react';
import { navigate, routes } from '../../../app/router';
import { Alert } from '../../auth/components/FormControls';
import {
  MerchantApiError,
  updateMerchantProfile,
  type MerchantResponse,
  type MerchantProfileUpdate,
} from '../api/merchantApi';
import styles from './MerchantProfile.module.css';

type ProfileField = {
  key: keyof MerchantProfileUpdate;
  label: string;
  max: number;
  required?: boolean;
  type?: 'email' | 'tel';
  autoComplete: string;
};

const sections: { title: string; fields: ProfileField[] }[] = [
  {
    title: 'Business information',
    fields: [{ key: 'businessName', label: 'Business name', max: 150, required: true, autoComplete: 'organization' }],
  },
  {
    title: 'Contact details',
    fields: [
      { key: 'businessEmail', label: 'Business email', max: 150, required: true, type: 'email', autoComplete: 'email' },
      { key: 'contactNumber', label: 'Contact phone number', max: 20, required: true, type: 'tel', autoComplete: 'tel' },
    ],
  },
  {
    title: 'Business address',
    fields: [
      { key: 'addressLine1', label: 'Address line 1', max: 255, required: true, autoComplete: 'address-line1' },
      { key: 'addressLine2', label: 'Address line 2', max: 255, autoComplete: 'address-line2' },
      { key: 'city', label: 'City', max: 100, required: true, autoComplete: 'address-level2' },
      { key: 'province', label: 'Province / state', max: 100, autoComplete: 'address-level1' },
      { key: 'postalCode', label: 'Postal code', max: 20, autoComplete: 'postal-code' },
      { key: 'country', label: 'Country', max: 100, required: true, autoComplete: 'country-name' },
    ],
  },
];

const fields = sections.flatMap(({ fields }) => fields);
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function profileValues(profile?: MerchantResponse): MerchantProfileUpdate {
  return {
    businessName: profile?.businessName ?? '',
    businessEmail: profile?.businessEmail ?? '',
    contactNumber: profile?.contactNumber ?? '',
    addressLine1: profile?.addressLine1 ?? '',
    addressLine2: profile?.addressLine2 ?? '',
    city: profile?.city ?? '',
    province: profile?.province ?? '',
    postalCode: profile?.postalCode ?? '',
    country: profile?.country ?? '',
  };
}

interface BusinessProfileFormProps {
  initialProfile?: MerchantResponse;
  onProfileUpdated?: (updated: MerchantResponse) => void;
}

export function BusinessProfileForm({ initialProfile, onProfileUpdated }: BusinessProfileFormProps) {
  const preview = initialProfile === undefined;
  const legacy = onProfileUpdated !== undefined;
  const [profile, setProfile] = useState(initialProfile);
  const [values, setValues] = useState(() => profileValues(initialProfile));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; message: string }>();
  const [saving, setSaving] = useState(false);
  const request = useRef<AbortController | null>(null);

  useEffect(() => () => request.current?.abort(), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current) return;

    const form = event.currentTarget;
    const details = { ...values };
    const errors: Record<string, string> = {};
    for (const { key, label, required, max, type } of fields) {
      const value = details[key].trim();
      details[key] = value;
      if (required && !value) {
        errors[key] = `${label} is required.`;
      } else if (value.length > max) {
        errors[key] = `${label} cannot exceed ${max} characters.`;
      } else if (type === 'email' && value && !emailPattern.test(value)) {
        errors[key] = 'Please provide a valid email address.';
      }
    }

    setFieldErrors(errors);
    const firstError = Object.keys(errors)[0];
    if (firstError) {
      setFeedback({ kind: 'error', message: 'Please correct the highlighted fields before saving.' });
      const input = form.elements.namedItem(firstError);
      if (input instanceof HTMLInputElement) input.focus();
      return;
    }

    if (preview) {
      setFeedback({ kind: 'success', message: 'Form validation passed. Sign in as a Merchant to save your business profile.' });
      return;
    }

    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    setFeedback(undefined);

    try {
      const updated = await updateMerchantProfile(details, controller.signal);
      if (controller.signal.aborted) return;
      setProfile(updated);
      setValues(profileValues(updated));
      onProfileUpdated?.(updated);
      setFeedback({ kind: 'success', message: legacy ? 'Business profile updated successfully.' : 'Business profile saved.' });
    } catch (error) {
      if (controller.signal.aborted) return;
      if (legacy && error instanceof MerchantApiError && error.status === 401) {
        navigate(routes.login);
        return;
      }
      if (error instanceof MerchantApiError) setFieldErrors(error.fieldErrors);
      const message = legacy && error instanceof MerchantApiError && error.status === 403
        ? 'Access forbidden. You do not have permission to update this merchant profile.'
        : error instanceof Error && !(error instanceof SyntaxError)
          ? error.message
          : 'Could not save the business profile. Please try again.';
      setFeedback({ kind: 'error', message });
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setSaving(false);
    }
  }

  const saveLabel = legacy
    ? profile?.complete ? 'Update Business Profile' : 'Complete Business Profile'
    : 'Save business profile';

  return (
    <section className={styles.profile} aria-labelledby="profile-title">
      <div className={styles.headerRow}>
        <div>
          <h2 id="profile-title" className={styles.title}>{legacy ? 'Business Profile' : 'Business details'}</h2>
          <p className={styles.subtitle}>
            {preview ? 'Sign in as a Merchant to save your business profile.' : 'Manage the sender details used on your deliveries.'}
          </p>
        </div>
        {profile && (
          <span className={profile.complete ? styles.badgeComplete : styles.badgeIncomplete} role="status">
            {profile.complete ? 'Profile complete' : 'Profile incomplete'}
          </span>
        )}
      </div>

      <form className={styles.form} onSubmit={(event) => void submit(event)} noValidate>
        <p className={styles.requiredNote}>Fields marked with <span>*</span> are required.</p>
        {sections.map(({ title, fields }) => (
          <fieldset key={title} className={styles.section} disabled={saving}>
            <legend>{title}</legend>
            <div className={styles.fieldGrid}>
              {fields.map(({ key, label, type = 'text', required, max, autoComplete }) => (
                <div className={styles.fieldGroup} key={key}>
                  <label className={styles.label} htmlFor={key}>
                    {label}{required
                      ? <span className={styles.requiredMark} aria-hidden="true"> *</span>
                      : <span className={styles.optionalTag}> (optional)</span>}
                  </label>
                  <input
                    id={key}
                    name={key}
                    className={styles.input}
                    type={type}
                    required={required}
                    maxLength={max}
                    autoComplete={autoComplete}
                    value={values[key]}
                    aria-invalid={Boolean(fieldErrors[key])}
                    aria-describedby={fieldErrors[key] ? `err-${key}` : undefined}
                    onChange={(event) => {
                      setValues({ ...values, [key]: event.target.value });
                      setFieldErrors({ ...fieldErrors, [key]: '' });
                      setFeedback(undefined);
                    }}
                  />
                  {fieldErrors[key] && <span id={`err-${key}`} className={styles.fieldError}>{fieldErrors[key]}</span>}
                </div>
              ))}
              {title === 'Business information' && profile && (
                <div className={styles.fieldGroup}>
                  <label className={styles.label} htmlFor="ownerName">Account owner</label>
                  <input id="ownerName" className={`${styles.input} ${styles.inputReadOnly}`} value={profile.ownerName || '—'} readOnly />
                </div>
              )}
            </div>
          </fieldset>
        ))}

        {feedback && <Alert kind={feedback.kind}>{feedback.message}</Alert>}
        <div className={styles.buttonRow}>
          <button className={styles.primaryButton} disabled={saving} type="submit">
            {saving ? 'Saving…' : preview ? 'Validate form' : saveLabel}
          </button>
          {saving && <span className={styles.mutedText} role="status">Saving your profile…</span>}
        </div>
      </form>

      {legacy && profile && (
        <div className={styles.metaRow}>
          <span>Merchant ID: #{profile.id}</span>
          {profile.updatedAt && (
            <span>Last updated: {new Date(profile.updatedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</span>
          )}
        </div>
      )}
    </section>
  );
}
