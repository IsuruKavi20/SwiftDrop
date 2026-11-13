import { useEffect, useRef, useState, type FormEvent } from 'react';
import { findPublicTracking, type PublicTracking } from '../api/publicTrackingApi';
import styles from './MerchantPages.module.css';

type Lookup = { status: 'idle' | 'loading' | 'missing' | 'error' } | { status: 'ready'; delivery: PublicTracking };

function statusLabel(status: string) {
  return status.toLowerCase().replaceAll('_', ' ');
}

export function TrackingPage() {
  const [number, setNumber] = useState('');
  const [lookup, setLookup] = useState<Lookup>({ status: 'idle' });
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trackingNumber = number.trim();
    if (!trackingNumber || request.current) return;

    const controller = new AbortController();
    request.current = controller;
    setLookup({ status: 'loading' });
    try {
      const delivery = await findPublicTracking(trackingNumber, controller.signal);
      if (!controller.signal.aborted) setLookup(delivery ? { status: 'ready', delivery } : { status: 'missing' });
    } catch {
      if (!controller.signal.aborted) setLookup({ status: 'error' });
    } finally {
      request.current = null;
    }
  }

  return (
    <section className={styles.panel} aria-labelledby="tracking-title">
      <h2 id="tracking-title">Public tracking lookup</h2>
      <p>Enter a SwiftDrop tracking number to see its public delivery status and tracking history.</p>
      <form className={styles.lookup} onSubmit={(event) => void search(event)}>
        <label><span>Tracking number</span><input type="search" required value={number}
          disabled={lookup.status === 'loading'} onChange={(event) => {
            setNumber(event.target.value);
            setLookup({ status: 'idle' });
          }} /></label>
        <button className={styles.button} disabled={!number.trim() || lookup.status === 'loading'} type="submit">Track delivery</button>
      </form>
      {lookup.status === 'idle' && <p className={styles.state}>Tracking results will appear here after a lookup.</p>}
      {lookup.status === 'loading' && <p role="status">Looking up delivery…</p>}
      {lookup.status === 'missing' && <p role="status">No delivery found for that tracking number.</p>}
      {lookup.status === 'error' && <p role="alert" className={styles.error}>Tracking is temporarily unavailable. Try again.</p>}
      {lookup.status === 'ready' && <div className={styles.state}>
        <h3>{lookup.delivery.trackingNumber}</h3>
        <p role="status">Status: <strong>{statusLabel(lookup.delivery.status)}</strong></p>
        {(lookup.delivery.fromCity || lookup.delivery.toCity) &&
          <p>From {lookup.delivery.fromCity || 'an unavailable origin'} to {lookup.delivery.toCity || 'an unavailable destination'}</p>}
        {lookup.delivery.events.length ? <ol className={styles.timeline}>
          {lookup.delivery.events.map((event, index) => (
            <li key={`${event.eventTime}-${index}`}>
              <strong>{statusLabel(event.status)}</strong>
              {event.description && <p>{event.description}</p>}
              {event.locationName && <p>{event.locationName}</p>}
              <time dateTime={event.eventTime}>{new Date(event.eventTime).toLocaleString()}</time>
            </li>
          ))}
        </ol> : <p>No tracking events have been recorded.</p>}
      </div>}
    </section>
  );
}
