import { listCourierWorkloads } from '../api/dispatcherApi';
import type { CourierAvailability } from '../api/dispatcherTypes';
import { AvailabilityBadge } from '../components/Badges';
import { PageHeader } from '../components/DispatcherLayout';
import { Dropdown } from '../components/Dropdown';
import { availabilityLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { EmptyState, LoadError } from '../components/States';
import { useUrlState } from '../lib/useUrlState';
import { SortHeader } from '../components/SortHeader';
import { useSort } from '../lib/useSort';
import { PersonName } from '../components/PersonName';
import { personName } from '../lib/names';
import styles from '../components/Dispatcher.module.css';

const AVAILABILITIES = Object.keys(availabilityLabels) as CourierAvailability[];

export function CourierWorkloadsPage({ preview }: { preview: boolean }) {
  const [availability, setAvailability] = useUrlState<CourierAvailability | ''>('availability', '', ['', ...AVAILABILITIES]);
  const { data, error, loading, reload } = useDispatchData(() => listCourierWorkloads(availability || undefined), [availability]);
  const { rows, sort, toggle } = useSort(data, {
    courier: (c) => personName(c.name).display, vehicle: (c) => c.vehicleType, availability: (c) => ({ AVAILABLE: 0, BUSY: 1, OFFLINE: 2 })[c.availability], active: (c) => c.activeDeliveries,
  });

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="Courier Workloads"
        subtitle="Active deliveries per courier. Only Available couriers can be given new parcels." />

      <div className={styles.toolbar}>
        <label className={styles.select}>
          <span className={styles.visuallyHidden}>Availability</span>
          <Dropdown value={availability} onChange={(v) => setAvailability(v as CourierAvailability | '')}
            options={[{ value: '', label: 'All couriers' }, ...AVAILABILITIES.map((a) => ({ value: a, label: availabilityLabels[a] }))]} />
        </label>
      </div>

      {error && <LoadError message={error} onRetry={reload} />}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <SortHeader label="Courier" column="courier" sort={sort} onSort={toggle} />
                <SortHeader label="Vehicle" column="vehicle" sort={sort} onSort={toggle} />
                <SortHeader label="Availability" column="availability" sort={sort} onSort={toggle} />
                <SortHeader label="Active" column="active" sort={sort} onSort={toggle} numeric />
                <th scope="col">Current parcels</th>
              </tr>
            </thead>
            <tbody>
              {rows?.map((c) => (
                <tr key={c.courierId}>
                  <td>
                    <PersonName name={c.name} id={c.userId ?? String(c.courierId)}>
                      <a className={styles.cellSub} href={`tel:${c.phoneNumber}`}>{c.phoneNumber}</a>
                    </PersonName>
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
        {data?.length === 0 && (
          <EmptyState icon="truck" title={availability ? 'No couriers with this availability' : 'No couriers yet'}
            action={availability && <button className={styles.secondaryButton} type="button" onClick={() => setAvailability('')}>Show all couriers</button>}>
            {availability ? 'Pick another availability to see more couriers.' : 'Add courier profiles on the Couriers page.'}
          </EmptyState>
        )}
      </div>
    </>
  );
}
