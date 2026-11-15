import { useState } from 'react';
import { listCourierWorkloads } from '../api/dispatcherApi';
import type { CourierAvailability } from '../api/dispatcherTypes';
import { AvailabilityBadge } from '../components/Badges';
import { PageHeader } from '../components/DispatcherLayout';
import { availabilityLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

const AVAILABILITIES = Object.keys(availabilityLabels) as CourierAvailability[];

export function CourierWorkloadsPage({ preview }: { preview: boolean }) {
  const [availability, setAvailability] = useState<CourierAvailability | ''>('');
  const { data, error, loading } = useDispatchData(() => listCourierWorkloads(availability || undefined), [availability]);

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="Courier Workloads"
        subtitle="Active deliveries per courier. Only Available couriers can be given new parcels." />

      <div className={styles.toolbar}>
        <label className={styles.select}>
          <span className={styles.visuallyHidden}>Availability</span>
          <select value={availability} onChange={(e) => setAvailability(e.target.value as CourierAvailability | '')}>
            <option value="">All couriers</option>
            {AVAILABILITIES.map((a) => <option key={a} value={a}>{availabilityLabels[a]}</option>)}
          </select>
        </label>
      </div>

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Courier</th>
                <th scope="col">Vehicle</th>
                <th scope="col">Availability</th>
                <th scope="col" className={styles.numeric}>Active</th>
                <th scope="col">Current parcels</th>
              </tr>
            </thead>
            <tbody>
              {data?.map((c) => (
                <tr key={c.courierId}>
                  <td>
                    <strong>{c.name}</strong>
                    <a className={styles.cellSub} href={`tel:${c.phoneNumber}`}>{c.phoneNumber}</a>
                  </td>
                  <td>
                    {c.vehicleType ?? <span className={styles.muted}>—</span>}
                    {c.vehicleNumber && <small className={styles.cellSub}>{c.vehicleNumber}</small>}
                  </td>
                  <td><AvailabilityBadge availability={c.availability} /></td>
                  <td className={`${styles.numeric} ${styles.strongNumber}`}>{c.activeDeliveries}</td>
                  <td className={styles.trackingList}>
                    {c.currentTrackingNumbers.length ? c.currentTrackingNumbers.join(', ') : <span className={styles.muted}>—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data?.length === 0 && <p className={styles.empty}>No couriers with this availability.</p>}
      </div>
    </>
  );
}
