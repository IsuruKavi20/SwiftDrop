import { searchDeliveries } from '../api/dispatcherApi';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { formatDateTime, latestFailure } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDispatchData } from '../lib/useDispatchData';
import { EmptyState, LoadError } from '../components/States';
import { SortHeader } from '../components/SortHeader';
import { useSort } from '../lib/useSort';
import styles from '../components/Dispatcher.module.css';

export function FailedDeliveriesPage({ preview }: { preview: boolean }) {
  const { data, error, loading, reload } = useDispatchData(() => searchDeliveries({ status: 'FAILED', size: 100 }), []);
  const { rows, sort, toggle } = useSort(data?.items, {
    tracking: (d) => d.trackingNumber, courier: (d) => d.courier?.name, failed: (d) => latestFailure(d).latest?.eventTime,
  });
  const { openDetails, openAssign, dialogs } = useDeliveryDialogs();

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="Failed Deliveries"
        subtitle="Assign or reassign a courier for another attempt. Earlier assignments and the failed attempt stay in the delivery's history." />

      {error && <LoadError message={error} onRetry={reload} />}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <SortHeader label="Tracking no." column="tracking" sort={sort} onSort={toggle} />
                <SortHeader label="Courier" column="courier" sort={sort} onSort={toggle} />
                <th scope="col">Failure reason</th>
                <SortHeader label="Failed attempt" column="failed" sort={sort} onSort={toggle} />
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {rows?.map((d) => {
                const { latest, attempts } = latestFailure(d);
                return (
                  <tr key={d.id}>
                    <td className={styles.mono}>{d.trackingNumber}</td>
                    <td>{d.courier?.name ?? <span className={styles.muted}>Not assigned</span>}</td>
                    <td className={styles.reasonCell}>
                      {latest?.description ?? 'No reason recorded'}
                      {latest?.locationName && <small className={styles.cellSub}>{latest.locationName}</small>}
                    </td>
                    <td className={styles.dateCell}>
                      {latest ? formatDateTime(latest.eventTime) : '—'}
                      <small className={styles.cellSub}>Attempt {Math.max(attempts, 1)}</small>
                    </td>
                    <td className={styles.actionCell}>
                      <div className={styles.rowActions}>
                        <button className={styles.secondaryButton} type="button" onClick={() => openDetails(d.id)}>View details</button>
                        <button className={styles.primaryButton} type="button" onClick={() => openAssign(d)}>
                          <DispatcherIcon name={d.courier ? 'reassign' : 'assign'} size={18} />
                          {d.courier ? 'Reassign courier' : 'Assign courier'}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {data?.totalItems === 0 && (
          <EmptyState icon="check" title="No failed deliveries" positive>Every delivery attempt has gone through. Nice work.</EmptyState>
        )}
      </div>
      {dialogs}
    </>
  );
}
