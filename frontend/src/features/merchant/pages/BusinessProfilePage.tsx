import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../auth/components/FormControls';
import { getMerchantProfile, type MerchantProfile } from '../api/merchantApi';
import { BusinessProfileForm } from '../components/BusinessProfileForm';
import styles from '../components/MerchantProfile.module.css';

type ProfileLoad = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; profile: MerchantProfile };

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
      (error: unknown) => {
        if (!active) return;
        const message = error instanceof Error && !(error instanceof SyntaxError)
          ? error.message
          : 'The profile response could not be read. Try again.';
        setLoad({ status: 'error', message });
      },
    );
    return () => { active = false; };
  }, [attempt, preview]);

  function retry() {
    request.current = null;
    setLoad({ status: 'loading' });
    setAttempt((previous) => previous + 1);
  }

  if (preview) return <BusinessProfileForm />;
  if (load.status === 'ready') return <BusinessProfileForm initialProfile={load.profile} />;

  return (
    <section className={`${styles.profile} ${styles.card}`} aria-labelledby="profile-title">
      <h2 id="profile-title" className={styles.title}>Business details</h2>
      {load.status === 'loading' ? (
        <p className={styles.loadingState} role="status">Loading business profile…</p>
      ) : (
        <div className={styles.loadError}>
          <Alert kind="error">{load.message}</Alert>
          <button className={styles.ghostButton} type="button" onClick={retry}>Try again</button>
        </div>
      )}
    </section>
  );
}
