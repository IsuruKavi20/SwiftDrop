import { useState } from 'react';
import { getDeliveryReport } from '../api/adminApi';
import { DeliveriesChart } from '../components/DeliveriesChart';
import { PageHeader } from '../components/DispatcherLayout';
import { formatPercent, successRate } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

const RANGES = [7, 14, 30] as const;

export function ReportsPage() {
  const [days, setDays] = useState<(typeof RANGES)[number]>(7);
  const { data, error, loading } = useDispatchData(() => getDeliveryReport(days), [days]);
  const rate = data ? successRate(data.delivered, data.failedAttempts) : null;

  return (
    <>
      <PageHeader title="Reports" subtitle={`Last ${days} days, including today`}
        action={(
          <label className={styles.select}>
            <span className={styles.visuallyHidden}>Period</span>
            <select value={days} onChange={(e) => setDays(Number(e.target.value) as (typeof RANGES)[number])}>
              {RANGES.map((r) => <option key={r} value={r}>Last {r} days</option>)}
            </select>
          </label>
        )} />
      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <ul className={styles.statGrid} aria-busy={loading}>
        <li className={styles.statCard}>
          <div className={styles.statTop}><span>Bookings created</span></div>
          <strong>{data?.bookingsCreated ?? '–'}</strong>
          <small>New deliveries in the period</small>
        </li>
        <li className={styles.statCard}>
          <div className={styles.statTop}><span>Delivered</span></div>
          <strong data-tone="green">{data?.delivered ?? '–'}</strong>
          <small>By delivery date</small>
        </li>
        <li className={styles.statCard}>
          <div className={styles.statTop}><span>Failed attempts</span></div>
          <strong data-tone="red">{data?.failedAttempts ?? '–'}</strong>
          <small>Including parcels delivered on a later try</small>
        </li>
        <li className={styles.statCard}>
          <div className={styles.statTop}><span>Success rate</span></div>
          <strong>{formatPercent(rate)}</strong>
          <small>{data ? `${data.delivered} ÷ (${data.delivered} + ${data.failedAttempts})` : '–'}</small>
        </li>
      </ul>

      <section className={styles.panel}>
        <header className={styles.panelHeader}>
          <div>
            <h2>Deliveries per day</h2>
            <p>Success rate = delivered ÷ (delivered + failed attempts) in the period. Parcels still in progress are left out.</p>
          </div>
        </header>
        {data && <DeliveriesChart days={data.perDay} />}
      </section>

      <h2 className={styles.sectionTitle} style={{ marginTop: '1.75rem' }}>Courier summary</h2>
      <div className={styles.tableCard}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Courier</th>
                <th scope="col" className={styles.numeric}>Delivered</th>
                <th scope="col" className={styles.numeric}>Failed attempts</th>
                <th scope="col" className={styles.numeric}>Success rate</th>
                <th scope="col" className={styles.numeric}>Active now</th>
              </tr>
            </thead>
            <tbody>
              {data?.couriers.map((c) => (
                <tr key={c.courierId}>
                  <td><strong>{c.name}</strong></td>
                  <td className={styles.numeric}>{c.delivered}</td>
                  <td className={styles.numeric}>{c.failedAttempts}</td>
                  <td className={styles.numeric}>{formatPercent(successRate(c.delivered, c.failedAttempts))}</td>
                  <td className={styles.numeric}>{c.active}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
