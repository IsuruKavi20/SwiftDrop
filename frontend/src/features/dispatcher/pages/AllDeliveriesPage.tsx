import { useState } from 'react';
import { searchDeliveries } from '../api/dispatcherApi';
import type { DeliveryStatus } from '../api/dispatcherTypes';
import { StatusBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { SearchBox } from '../components/SearchBox';
import { formatDate, formatFee, statusLabels } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDebounced, useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

const PAGE_SIZE = 10;
const STATUSES = Object.keys(statusLabels) as DeliveryStatus[];

export function AllDeliveriesPage({ preview }: { preview: boolean }) {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<DeliveryStatus | ''>('');
  const [page, setPage] = useState(0);
  const text = useDebounced(query);
  const { data, error, loading } = useDispatchData(
    () => searchDeliveries({ status: status || undefined, text, page, size: PAGE_SIZE }),
    [status, text, page],
  );
  const { openDetails, dialogs } = useDeliveryDialogs();

  const first = data && data.totalItems > 0 ? data.page * data.size + 1 : 0;
  const last = data ? data.page * data.size + data.items.length : 0;

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="All Deliveries"
        subtitle="Every delivery from customers and merchants, newest bookings first." />

      <div className={styles.toolbar}>
        <SearchBox value={query} onChange={(value) => { setQuery(value); setPage(0); }} label="Search deliveries"
          placeholder="Search tracking number, courier, name, street, city or district" />
        <label className={styles.select}>
          <span className={styles.visuallyHidden}>Status</span>
          <select value={status} onChange={(e) => { setStatus(e.target.value as DeliveryStatus | ''); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{statusLabels[s]}</option>)}
          </select>
        </label>
      </div>

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Tracking no.</th>
                <th scope="col">Destination</th>
                <th scope="col">Booked</th>
                <th scope="col">Courier</th>
                <th scope="col" className={styles.numeric}>Fee</th>
                <th scope="col">Status</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.items.map((d) => (
                <tr key={d.id}>
                  <td className={styles.mono}>{d.trackingNumber}</td>
                  <td>{d.destination.city}<small className={styles.cellSub}>{d.destination.contactName}</small></td>
                  <td className={styles.dateCell}>{formatDate(d.createdAt)}</td>
                  <td>{d.courier?.name ?? <span className={styles.muted}>—</span>}</td>
                  <td className={styles.numeric}>{formatFee(d.estimatedFee)}</td>
                  <td><StatusBadge status={d.status} /></td>
                  <td className={styles.actionCell}>
                    <button className={styles.secondaryButton} type="button" onClick={() => openDetails(d.id)}>View details</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data?.totalItems === 0 && <p className={styles.empty}>No deliveries match these filters.</p>}
      </div>

      {data && data.totalItems > 0 && (
        <nav className={styles.pagination} aria-label="Pages">
          <span>Showing {first}–{last} of {data.totalItems}</span>
          <div>
            <button className={styles.secondaryButton} type="button" aria-label="Previous page"
              disabled={data.page === 0} onClick={() => setPage(data.page - 1)}>
              <DispatcherIcon name="left" size={18} />
            </button>
            <span>Page {data.page + 1} of {data.totalPages}</span>
            <button className={styles.secondaryButton} type="button" aria-label="Next page"
              disabled={data.page + 1 >= data.totalPages} onClick={() => setPage(data.page + 1)}>
              <DispatcherIcon name="right" size={18} />
            </button>
          </div>
        </nav>
      )}
      {dialogs}
    </>
  );
}
