import { searchDeliveries } from '../api/dispatcherApi';
import type { DeliveryStatus } from '../api/dispatcherTypes';
import { StatusBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { Dropdown } from '../components/Dropdown';
import { SearchBox } from '../components/SearchBox';
import { formatDate, formatFee, statusLabels } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDebounced, useDispatchData } from '../lib/useDispatchData';
import { EmptyState, LoadError } from '../components/States';
import { useUrlNumber, useUrlState } from '../lib/useUrlState';
import styles from '../components/Dispatcher.module.css';

const PAGE_SIZE = 10;
const STATUSES = Object.keys(statusLabels) as DeliveryStatus[];

export function AllDeliveriesPage({ preview }: { preview: boolean }) {
  // Kept in the address bar: ?q=…&status=…&page=2 (page shown 1-based)
  const [query, setQuery] = useUrlState<string>('q', '');
  const [status, setStatus] = useUrlState<DeliveryStatus | ''>('status', '', ['', ...STATUSES]);
  const [page, setPage] = useUrlNumber('page', 0, { offset: 1 });
  const text = useDebounced(query);
  const { data, error, loading, reload } = useDispatchData(
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
          <Dropdown value={status} onChange={(v) => { setStatus(v as DeliveryStatus | ''); setPage(0); }}
            options={[{ value: '', label: 'All statuses' }, ...STATUSES.map((s) => ({ value: s, label: statusLabels[s] }))]} />
        </label>
      </div>

      {error && <LoadError message={error} onRetry={reload} />}

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
        {data?.totalItems === 0 && (
          <EmptyState icon="box" title={query || status ? 'No deliveries match' : 'No deliveries yet'}
            action={(query || status) && (
              <button className={styles.secondaryButton} type="button" onClick={() => { setQuery(''); setStatus(''); setPage(0); }}>Clear filters</button>
            )}>
            {query || status ? 'Try another search or status.' : 'Bookings from customers and merchants show up here.'}
          </EmptyState>
        )}
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
