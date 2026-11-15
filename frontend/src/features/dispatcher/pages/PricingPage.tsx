import { useState } from 'react';
import { allDistricts, getPublishedPricing, publishPricing, quote } from '../api/adminApi';
import type { PricingRates, PublishedPricing } from '../api/adminTypes';
import type { DeliveryType } from '../api/dispatcherTypes';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { formatDate, formatLkr } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

/** The rates CustomerPricingService uses, in the order of its formula. */
const RATE_FIELDS: { key: keyof PricingRates; label: string; hint: string }[] = [
  { key: 'sameDistrictBase', label: 'Base fee, same district', hint: 'Pickup and destination in the same district' },
  { key: 'otherDistrictBase', label: 'Base fee, other district', hint: 'Pickup and destination in different districts' },
  { key: 'additionalKg', label: 'Each additional kg', hint: 'Per kg of total parcel weight above the first kg' },
  { key: 'expressSurcharge', label: 'Express surcharge', hint: 'Added for Express deliveries' },
  { key: 'sameDaySurcharge', label: 'Same-day surcharge', hint: 'Added for Same-day deliveries' },
];

const deliveryTypeLabels: Record<DeliveryType, string> = { STANDARD: 'Standard', EXPRESS: 'Express', SAME_DAY: 'Same day' };

type Draft = Record<keyof PricingRates, string>;
const toDraft = (rates: PricingRates) => Object.fromEntries(RATE_FIELDS.map(({ key }) => [key, String(rates[key])])) as Draft;
const fromDraft = (draft: Draft) => Object.fromEntries(RATE_FIELDS.map(({ key }) => [key, Number(draft[key])])) as unknown as PricingRates;

function QuotePreview({ rates, usingDraft }: { rates: PricingRates; usingDraft: boolean }) {
  const [pickup, setPickup] = useState('Colombo');
  const [destination, setDestination] = useState('Kandy');
  const [weight, setWeight] = useState('2');
  const [type, setType] = useState<DeliveryType>('STANDARD');
  const kg = Number(weight);
  const valid = Number.isFinite(kg) && kg > 0 && Object.values(rates).every((r) => Number.isFinite(r) && r >= 0);
  const result = valid ? quote(rates, { pickupDistrict: pickup, destinationDistrict: destination, totalWeightKg: kg, deliveryType: type }) : undefined;

  return (
    <section className={styles.panel}>
      <header className={styles.panelHeader}>
        <div>
          <h2>Quote preview</h2>
          <p>{usingDraft ? 'Using your unsaved changes' : 'Using the published rates'}</p>
        </div>
      </header>
      <div className={styles.quoteForm}>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span>Pickup district</span>
            <select value={pickup} onChange={(e) => setPickup(e.target.value)}>
              {allDistricts.map((d) => <option key={d}>{d}</option>)}
            </select>
          </label>
          <label className={styles.field}>
            <span>Destination district</span>
            <select value={destination} onChange={(e) => setDestination(e.target.value)}>
              {allDistricts.map((d) => <option key={d}>{d}</option>)}
            </select>
          </label>
          <label className={styles.field}>
            <span>Total weight (kg)</span>
            <input type="number" min="0.1" step="0.1" value={weight} onChange={(e) => setWeight(e.target.value)} />
          </label>
          <label className={styles.field}>
            <span>Delivery type</span>
            <select value={type} onChange={(e) => setType(e.target.value as DeliveryType)}>
              {(Object.keys(deliveryTypeLabels) as DeliveryType[]).map((t) => <option key={t} value={t}>{deliveryTypeLabels[t]}</option>)}
            </select>
          </label>
        </div>
      </div>
      {result ? (
        <div className={styles.quoteResult} aria-live="polite">
          <div><span>Base ({pickup === destination ? 'same district' : 'other district'})</span><span>{formatLkr(result.baseFee)}</span></div>
          <div><span>Additional weight</span><span>{formatLkr(result.weightCharge)}</span></div>
          <div><span>Service ({deliveryTypeLabels[type]})</span><span>{formatLkr(result.serviceCharge)}</span></div>
          <div><span>Total</span><span>{formatLkr(result.totalFee)}</span></div>
        </div>
      ) : (
        <p className={styles.panelNote}>Enter a weight above 0 and valid rates to see a quote.</p>
      )}
    </section>
  );
}

function PricingEditor({ published, onPublished }: { published: PublishedPricing; onPublished: (message: string) => void }) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(published.rates));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const saved = toDraft(published.rates);
  const dirty = RATE_FIELDS.some(({ key }) => draft[key] !== saved[key]);
  const rates = fromDraft(draft);
  const invalid = Object.values(rates).some((r) => !Number.isFinite(r) || r < 0) || Object.values(draft).some((v) => v.trim() === '');

  async function publish() {
    setSaving(true);
    setError(undefined);
    try {
      const next = await publishPricing(rates);
      onPublished(`Rate version ${next.version} is published. New bookings are quoted with it; existing parcels keep their fee.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not publish. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader title="Pricing"
        subtitle={<>Published rate version <strong>v{published.version}</strong>, since {formatDate(published.publishedAt)}. Existing parcels keep the fee they were booked with.</>}
        action={(
          <div className={styles.rowActions}>
            <button className={styles.secondaryButton} type="button" disabled={!dirty || saving} onClick={() => { setDraft(saved); setError(undefined); }}>
              <DispatcherIcon name="reassign" size={16} /> Discard changes
            </button>
            <button className={styles.primaryButton} type="button" disabled={!dirty || invalid || saving} onClick={() => void publish()}>
              {saving ? 'Publishing…' : 'Save and publish'}
            </button>
          </div>
        )} />
      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.pricingGrid}>
        <section className={styles.panel}>
          <header className={styles.panelHeader}>
            <div>
              <h2>Rates</h2>
              <p>Fee = base fee + additional weight + service surcharge (the backend's CustomerPricingService formula).</p>
            </div>
          </header>
          <ul className={styles.rateRows}>
            {RATE_FIELDS.map(({ key, label, hint }) => (
              <li key={key}>
                <label htmlFor={`rate-${key}`}><strong>{label}</strong><small>{hint}</small></label>
                <span className={styles.money}>
                  <span>LKR</span>
                  <input id={`rate-${key}`} type="number" min="0" step="10" inputMode="decimal" value={draft[key]}
                    data-changed={draft[key] !== saved[key] || undefined}
                    onChange={(e) => setDraft({ ...draft, [key]: e.target.value })} />
                </span>
              </li>
            ))}
          </ul>
        </section>
        <QuotePreview rates={rates} usingDraft={dirty} />
      </div>
    </>
  );
}

export function PricingPage() {
  const { data, error } = useDispatchData(getPublishedPricing, []);
  const { showToast, toast } = useToast();
  return (
    <>
      {error && <p className={styles.errorBox} role="alert">{error}</p>}
      {/* Keyed by version, so the draft starts again from what was just published */}
      {data ? <PricingEditor key={data.version} published={data} onPublished={showToast} /> : <PageHeader title="Pricing" />}
      {toast}
    </>
  );
}
