import { searchDeliveries } from '../api/dispatcherApi';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { formatDateTime, latestFailure } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

export function FailedDeliveriesPage({ preview }: { preview: boolean }) {
  const { data, error, loading } = useDispatchData(() => searchDeliveries({ status: 'FAILED', size: 100 }), []);
  const { openDetails, openAssign, dialogs } = useDeliveryDialogs();

  return (
    <>
      <PageHeader breadcrumb preview={preview} title="Failed Deliveries"
        subtitle="Assign or reassign a courier for another attempt. Earlier assignments and the failed attempt stay in the delivery's history." />

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Tracking no.</th>
                <th scope="col">Courier</th>
                <th scope="col">Failure reason</th>
                <th scope="col">Failed attempt</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.items.map((d) => {
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
        {data?.totalItems === 0 && <p className={styles.empty}>No failed deliveries. Nice work.</p>}
      </div>
      {dialogs}
    </>
  );
}
