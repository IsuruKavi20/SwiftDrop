import { listUnassignedDeliveries } from '../api/dispatcherApi';
import { SourceBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { SearchBox } from '../components/SearchBox';
import { formatDateTime, parcelSummary, waitingLabel } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDebounced, useDispatchData } from '../lib/useDispatchData';
import { EmptyState, LoadError } from '../components/States';
import { useUrlState } from '../lib/useUrlState';
import { SortHeader } from '../components/SortHeader';
import { useSort } from '../lib/useSort';
import styles from '../components/Dispatcher.module.css';

export function UnassignedParcelsPage({ preview }: { preview: boolean }) {
  const [query, setQuery] = useUrlState<string>('q', '');
  const text = useDebounced(query);
  const { data, error, loading, reload } = useDispatchData(() => listUnassignedDeliveries(text), [text]);
  const { openDetails, openAssign, dialogs } = useDeliveryDialogs();
  const { rows, sort, toggle } = useSort(data, {
    tracking: (d) => d.trackingNumber, pickup: (d) => d.pickup.city, destination: (d) => d.destination.city,
    booked: (d) => d.createdAt, source: (d) => d.source,
  });

  function describe(count: number | undefined): string {
    if (count === undefined) return 'Loading…';
    const parcels = count === 1 ? 'parcel' : 'parcels';
    if (text) return `${count} matching ${parcels}`;
    return `${count} confirmed ${parcels} waiting for a courier, longest waiting first.`;
  }
  const subtitle = describe(data?.length);

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="Unassigned Parcels" subtitle={subtitle} />

      <div className={styles.toolbar}>
        <SearchBox value={query} onChange={setQuery} label="Search unassigned parcels"
          placeholder="Search tracking number, name, street, city or district" />
      </div>

      {error && <LoadError message={error} onRetry={reload} />}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <SortHeader label="Tracking no." column="tracking" sort={sort} onSort={toggle} />
                <SortHeader label="Pickup" column="pickup" sort={sort} onSort={toggle} />
                <SortHeader label="Destination" column="destination" sort={sort} onSort={toggle} />
                <SortHeader label="Booked" column="booked" sort={sort} onSort={toggle} />
                <th scope="col">Weight</th>
                <SortHeader label="Booked by" column="source" sort={sort} onSort={toggle} />
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {rows?.map((d) => (
                <tr key={d.id}>
                  <td>
                    <button className={styles.linkCell} type="button" onClick={() => openDetails(d.id)}>{d.trackingNumber}</button>
                    <small className={styles.cellSub}>{d.deliveryType.replace('_', ' ').toLowerCase()}</small>
                  </td>
                  <td>{d.pickup.city}<small className={styles.cellSub}>{d.pickup.district}</small></td>
                  <td>{d.destination.city}<small className={styles.cellSub}>{d.destination.contactName}</small></td>
                  <td className={styles.dateCell}>{formatDateTime(d.createdAt)}<small className={styles.cellSub}>waiting {waitingLabel(d.createdAt)}</small></td>
                  <td>{parcelSummary(d.parcels)}</td>
                  <td><SourceBadge source={d.source} /></td>
                  <td className={styles.actionCell}>
                    <button className={styles.primaryButton} type="button" onClick={() => openAssign(d)}>
                      <DispatcherIcon name="assign" size={18} /> Assign courier
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data?.length === 0 && (
          text ? (
            <EmptyState icon="search" title="No parcels match your search"
              action={<button className={styles.secondaryButton} type="button" onClick={() => setQuery('')}>Clear search</button>}>
              Try a tracking number, name, street or district.
            </EmptyState>
          ) : (
            <EmptyState icon="check" title="Every parcel has a courier" positive>New confirmed parcels waiting for a courier show up here.</EmptyState>
          )
        )}
      </div>
      {dialogs}
    </>
  );
}
