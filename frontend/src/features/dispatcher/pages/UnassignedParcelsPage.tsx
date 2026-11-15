import { useState } from 'react';
import { listUnassignedDeliveries } from '../api/dispatcherApi';
import { SourceBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { SearchBox } from '../components/SearchBox';
import { formatDateTime, parcelSummary, waitingLabel } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDebounced, useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

export function UnassignedParcelsPage({ preview }: { preview: boolean }) {
  const [query, setQuery] = useState('');
  const text = useDebounced(query);
  const { data, error, loading } = useDispatchData(() => listUnassignedDeliveries(text), [text]);
  const { openDetails, openAssign, dialogs } = useDeliveryDialogs();

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

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Tracking no.</th>
                <th scope="col">Pickup</th>
                <th scope="col">Destination</th>
                <th scope="col">Booked</th>
                <th scope="col">Weight</th>
                <th scope="col">Booked by</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.map((d) => (
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
          <p className={styles.empty}>{text ? 'No unassigned parcels match your search.' : 'Every confirmed parcel has a courier.'}</p>
        )}
      </div>
      {dialogs}
    </>
  );
}
