import { getStatusCounts, listCourierWorkloads, listUnassignedDeliveries, searchDeliveries } from '../api/dispatcherApi';
import { AvailabilityBadge, StatusBadge } from '../components/Badges';
import { DispatcherIcon, type DispatcherIconName } from '../components/DispatcherIcon';
import { DispatcherLink } from '../components/DispatcherLink';
import { PageHeader } from '../components/DispatcherLayout';
import { dispatcherPath } from '../dispatcherRoutes';
import { formatDateTime, formatToday, hoursSince, latestFailure, WAITING_ALERT_HOURS } from '../lib/format';
import { useDeliveryDialogs } from '../lib/useDeliveryDialogs';
import { useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

async function loadOverview() {
  const [counts, unassigned, failed, couriers] = await Promise.all([
    getStatusCounts(),
    listUnassignedDeliveries(),
    searchDeliveries({ status: 'FAILED', size: 50 }),
    listCourierWorkloads(),
  ]);
  return { counts, unassigned, failed: failed.items, couriers };
}

export function OverviewPage({ preview }: { preview: boolean }) {
  const { data, error, loading } = useDispatchData(loadOverview, []);
  const { openDetails, openAssign, dialogs } = useDeliveryDialogs();

  const counts = data?.counts;
  const cards: { label: string; value: number | undefined; icon: DispatcherIconName; tone: string; hint: string }[] = [
    { label: 'Unassigned parcels', value: data?.unassigned.length, icon: 'inbox', tone: 'amber', hint: 'Confirmed, no courier yet' },
    {
      label: 'Active deliveries', icon: 'truck', tone: 'blue', hint: 'Picked up, in transit or out for delivery',
      value: counts && counts.PICKED_UP + counts.IN_TRANSIT + counts.OUT_FOR_DELIVERY,
    },
    { label: 'Delivered', value: counts?.DELIVERED, icon: 'check', tone: 'green', hint: 'All completed deliveries' },
    { label: 'Failed', value: counts?.FAILED, icon: 'failed', tone: 'red', hint: 'Waiting for another attempt' },
  ];

  const waiting = data?.unassigned.filter((d) => hoursSince(d.createdAt) >= WAITING_ALERT_HOURS) ?? [];
  const attention = [...(data?.failed ?? []), ...waiting];
  const busiest = Math.max(1, ...(data?.couriers.map((c) => c.activeDeliveries) ?? []));

  return (
    <>
      <PageHeader title="Operations Overview" subtitle={formatToday()}
        action={(
          <DispatcherLink className={styles.primaryButton} href={dispatcherPath('unassigned', preview)}>
            <DispatcherIcon name="assign" size={18} /> Assign parcels
          </DispatcherLink>
        )} />

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <ul className={styles.statGrid} aria-busy={loading}>
        {cards.map((card) => (
          <li key={card.label} className={styles.statCard}>
            <div className={styles.statTop}>
              <span>{card.label}</span>
              <span className={styles.statIcon} data-tone={card.tone}><DispatcherIcon name={card.icon} size={18} /></span>
            </div>
            <strong data-tone={card.tone}>{card.value ?? '–'}</strong>
            <small>{card.hint}</small>
          </li>
        ))}
      </ul>

      <div className={styles.overviewGrid}>
        <section className={styles.panel}>
          <header className={styles.panelHeader}>
            <div>
              <h2>Parcels needing attention</h2>
              <p>Failed attempts and bookings waiting more than {WAITING_ALERT_HOURS} hours for a courier.</p>
            </div>
          </header>
          {data && attention.length === 0 && <p className={styles.empty}>Nothing needs attention right now.</p>}
          <ul className={styles.attentionList}>
            {attention.map((delivery) => {
              const failed = delivery.status === 'FAILED';
              const reason = failed
                ? latestFailure(delivery).latest?.description ?? 'Delivery attempt failed'
                : `Waiting since ${formatDateTime(delivery.createdAt)}`;
              return (
                <li key={delivery.id}>
                  <span className={styles.attentionIcon} data-tone={failed ? 'red' : 'amber'}>
                    <DispatcherIcon name="alert" size={18} />
                  </span>
                  <span className={styles.mono}>{delivery.trackingNumber}</span>
                  <StatusBadge status={delivery.status} />
                  <span className={styles.attentionText}>{reason}</span>
                  {failed ? (
                    <button className={styles.secondaryButton} type="button" onClick={() => openDetails(delivery.id)}>Review</button>
                  ) : (
                    <button className={styles.primaryButton} type="button" onClick={() => openAssign(delivery)}>Assign courier</button>
                  )}
                </li>
              );
            })}
          </ul>
        </section>

        <section className={styles.panel}>
          <header className={styles.panelHeader}>
            <h2>Courier workload</h2>
            <DispatcherLink className={styles.textLink} href={dispatcherPath('workloads', preview)}>Details</DispatcherLink>
          </header>
          <ul className={styles.workloadList}>
            {data?.couriers.map((c) => (
              <li key={c.courierId}>
                <div className={styles.workloadTop}>
                  <strong>{c.name}</strong>
                  <AvailabilityBadge availability={c.availability} />
                  <span className={styles.workloadCount}>{c.activeDeliveries}</span>
                </div>
                <div className={styles.bar} role="img" aria-label={`${c.activeDeliveries} active deliveries`}>
                  <span style={{ width: `${(c.activeDeliveries / busiest) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
          <p className={styles.panelNote}>Bars compare each courier's active deliveries with the busiest courier.</p>
        </section>
      </div>
      {dialogs}
    </>
  );
}
