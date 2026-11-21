import { useEffect, useRef, useState, type FormEvent } from 'react';
import { findPublicTracking, type PublicTracking } from '../api/publicTrackingApi';
import { MerchantIcon } from '../components/MerchantIcon';
import { ShipmentTimeline } from '../components/ShipmentTimeline';
import shared from './MerchantPages.module.css';
import actions from './MerchantOrders.module.css';
import styles from './StoreOrdersPage.module.css';

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
    <section className={styles.shipment} aria-labelledby="tracking-title">
      <div className={styles.detailCard}>
        <h2 id="tracking-title">Public tracking lookup</h2>
        <p>Enter a tracking number to see the shipment’s public status and history.</p>
        <form className={shared.lookup} onSubmit={(event) => void search(event)}>
          <label>
            <span>Tracking number</span>
            <input type="search" required value={number} disabled={lookup.status === 'loading'} onChange={(event) => {
              setNumber(event.target.value);
              setLookup({ status: 'idle' });
            }} />
          </label>
          <button className={actions.primaryButton} disabled={!number.trim() || lookup.status === 'loading'} type="submit">
            <MerchantIcon name="search" size={17} /> Track delivery
          </button>
        </form>
      </div>
      {lookup.status === 'loading' && <div className={styles.listState} role="status">
        <span className={styles.spinner} aria-hidden="true" /><strong>Looking up delivery…</strong>
      </div>}
      {lookup.status === 'missing' && <div className={styles.listState} role="status">
        <span className={styles.stateIcon}><MerchantIcon name="pin" /></span>
        <strong>No delivery found</strong><p>Check the tracking number and try again.</p>
      </div>}
      {lookup.status === 'error' && <div className={styles.listState}>
        <div role="alert"><h3>Tracking is temporarily unavailable</h3><p>Try the lookup again.</p></div>
      </div>}
      {lookup.status === 'ready' && <>
        <div className={styles.shipmentSummary}>
          <h3 className={styles.trackingNumber}>{lookup.delivery.trackingNumber}</h3>
          <span role="status" className={`${styles.statusBadge} ${styles.statusNeutral} ${styles.publicStatus}`}>
            {statusLabel(lookup.delivery.status)}
          </span>
          {(lookup.delivery.fromCity || lookup.delivery.toCity) && <dl className={styles.shipmentFacts}>
            <div><dt>Origin</dt><dd>{lookup.delivery.fromCity || '—'}</dd></div>
            <div><dt>Destination</dt><dd>{lookup.delivery.toCity || '—'}</dd></div>
          </dl>}
        </div>
        <section className={styles.detailCard}>
          <h3 className={styles.detailCardTitle}>Tracking History</h3>
          <ShipmentTimeline events={lookup.delivery.events} />
        </section>
      </>}
    </section>
  );
}
