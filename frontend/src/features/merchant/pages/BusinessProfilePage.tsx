import { useEffect, useRef, useState, type FormEvent } from 'react';
import { getMerchantProfile, updateMerchantProfile, type MerchantProfile, type MerchantProfileUpdate } from '../api/merchantApi';
import styles from './MerchantPages.module.css';

const fields = [
  { key: 'businessName', label: 'Business name', max: 150, required: true, type: 'text' },
  { key: 'businessEmail', label: 'Business email', max: 150, required: true, type: 'email' },
  { key: 'contactNumber', label: 'Contact number', max: 20, required: true, type: 'tel' },
  { key: 'addressLine1', label: 'Address line 1', max: 255, required: true, type: 'text' },
  { key: 'addressLine2', label: 'Address line 2', max: 255, required: false, type: 'text' },
  { key: 'city', label: 'City', max: 100, required: true, type: 'text' },
  { key: 'province', label: 'Province', max: 100, required: false, type: 'text' },
  { key: 'postalCode', label: 'Postal code', max: 20, required: false, type: 'text' },
  { key: 'country', label: 'Country', max: 100, required: true, type: 'text' },
] as const satisfies readonly { key: keyof MerchantProfileUpdate; label: string; max: number; required: boolean; type: string }[];

function profileFields(profile?: Pick<MerchantProfile, keyof MerchantProfileUpdate>): MerchantProfileUpdate {
  return {
    businessName: profile?.businessName?.trim() ?? '',
    businessEmail: profile?.businessEmail?.trim() ?? '',
    contactNumber: profile?.contactNumber?.trim() ?? '',
    addressLine1: profile?.addressLine1?.trim() ?? '',
    addressLine2: profile?.addressLine2?.trim() ?? '',
    city: profile?.city?.trim() ?? '',
    province: profile?.province?.trim() ?? '',
    postalCode: profile?.postalCode?.trim() ?? '',
    country: profile?.country?.trim() ?? '',
  };
}

type ProfileLoad = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; profile: MerchantProfile };

function messageFor(error: unknown) {
  return error instanceof Error && !(error instanceof SyntaxError)
    ? error.message
    : 'The profile response could not be read. Try again.';
}

export function BusinessProfilePage({ preview }: { preview: boolean }) {
  const [load, setLoad] = useState<ProfileLoad>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const request = useRef<Promise<MerchantProfile> | null>(null);

  useEffect(() => {
    if (preview) return;
    let active = true;
    // Reuse the request across effect replays. The first GET can create an empty profile on the server.
    request.current ??= getMerchantProfile();
    void request.current.then(
      (profile) => { if (active) setLoad({ status: 'ready', profile }); },
      (error: unknown) => { if (active) setLoad({ status: 'error', message: messageFor(error) }); },
    );
    return () => { active = false; };
  }, [attempt, preview]);

  function retry() {
    request.current = null;
    setLoad({ status: 'loading' });
    setAttempt((previous) => previous + 1);
  }

  return (
    <section className={styles.panel} aria-labelledby="profile-title">
      <h2 id="profile-title">Business details</h2>
      {preview ? <ProfileForm />
        : load.status === 'loading' ? <p role="status">Loading business profile…</p>
        : load.status === 'error' ? <div>
          <p role="alert" className={styles.error}>{load.message}</p>
          <button className={styles.button} type="button" onClick={retry}>Try again</button>
        </div>
        : <ProfileForm initialProfile={load.profile} />}
    </section>
  );
}

function ProfileForm({ initialProfile }: { initialProfile?: MerchantProfile }) {
  const preview = initialProfile === undefined;
  const [profile, setProfile] = useState(initialProfile);
  const [values, setValues] = useState(() => profileFields(initialProfile));
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'error' | 'success'; message: string }>();
  const request = useRef<AbortController | null>(null);

  useEffect(() => () => request.current?.abort(), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current) return;

    const details = profileFields(values);
    const blankField = fields.find(({ key, required }) => required && !details[key]);
    if (blankField) {
      setFeedback({ type: 'error', message: `Enter a value for ${blankField.label.toLowerCase()}.` });
      const input = event.currentTarget.elements.namedItem(blankField.key);
      if (input instanceof HTMLInputElement) input.focus();
      return;
    }

    if (preview) {
      setFeedback({ type: 'success', message: 'Form validation passed. Sign in as a Merchant to save your business profile.' });
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
      setValues(profileFields(updated));
      setFeedback({ type: 'success', message: 'Business profile saved.' });
    } catch (error) {
      if (!controller.signal.aborted) setFeedback({ type: 'error', message: messageFor(error) });
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setSaving(false);
    }
  }

  return (
    <form onSubmit={(event) => void submit(event)}>
      {preview ? <p>Use the fields below to test form validation. Saving requires signing in as a Merchant.</p>
        : <p>{profile?.complete ? 'Keep your business contact and address information current.'
          : 'Your profile is incomplete. Fill in the required fields to complete it.'}</p>}
      {profile?.ownerName && <p>Account owner: <strong>{profile.ownerName}</strong></p>}
      <fieldset className={styles.fieldset} disabled={saving}>
        <legend>Contact and business address</legend>
        <div className={styles.formGrid}>
          {fields.map(({ key, label, type, required, max }) => (
            <label key={key}>
              <span>{label}{!required && ' (optional)'}</span>
              <input name={key} type={type} required={required} maxLength={max} value={values[key]}
                onChange={(event) => {
                  setValues({ ...values, [key]: event.target.value });
                  setFeedback(undefined);
                }} />
            </label>
          ))}
        </div>
        <button className={styles.button} type="submit">
          {preview ? 'Validate form' : saving ? 'Saving…' : 'Save business profile'}
        </button>
      </fieldset>
      {saving && <p role="status">Saving your profile…</p>}
      {feedback && <p role={feedback.type === 'error' ? 'alert' : 'status'} className={styles[feedback.type]}>{feedback.message}</p>}
    </form>
  );
}
