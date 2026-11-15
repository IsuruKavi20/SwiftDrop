import { countBookingsToday, getDeliveryReport, listCourierProfiles, listMerchantApplications } from '../api/adminApi';
import { getStatusCounts } from '../api/dispatcherApi';
import { DeliveriesChart } from '../components/DeliveriesChart';
import { DispatcherIcon, type DispatcherIconName } from '../components/DispatcherIcon';
import { DispatcherLink } from '../components/DispatcherLink';
import { PageHeader } from '../components/DispatcherLayout';
import { dispatcherPath } from '../dispatcherRoutes';
import { formatPercent, formatToday, successRate } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import styles from '../components/Dispatcher.module.css';

async function loadAdminOverview() {
  const [today, couriers, pending, counts, week] = await Promise.all([
    countBookingsToday(), listCourierProfiles(), listMerchantApplications('PENDING'), getStatusCounts(), getDeliveryReport(7),
  ]);
  return { today, couriers, pending, counts, week };
}

export function AdminOverviewPage({ preview }: { preview: boolean }) {
  const { data, error, loading } = useDispatchData(loadAdminOverview, []);
  const counts = data?.counts;
  const onDuty = data?.couriers.filter((c) => c.availability !== 'OFFLINE').length;
  const inProgress = counts && counts.CONFIRMED + counts.PICKED_UP + counts.IN_TRANSIT + counts.OUT_FOR_DELIVERY;
  const rate = counts ? successRate(counts.DELIVERED, counts.FAILED) : null;

  const cards: { label: string; value: number | undefined; icon: DispatcherIconName; tone: string; hint: string }[] = [
    { label: 'Bookings today', value: data?.today, icon: 'box', tone: 'blue', hint: 'Deliveries created since midnight' },
    { label: 'Couriers on duty', value: onDuty, icon: 'truck', tone: 'green', hint: `Available or busy, of ${data?.couriers.length ?? '–'} couriers` },
    { label: 'Merchant approvals', value: data?.pending.length, icon: 'store', tone: 'amber', hint: 'Applications waiting for a decision' },
    { label: 'Failed deliveries', value: counts?.FAILED, icon: 'failed', tone: 'red', hint: 'Currently in Failed status' },
  ];

  return (
    <>
      <PageHeader title="Admin Overview" subtitle={formatToday()} />
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
              <h2>Deliveries per day</h2>
              <p>Last 7 days: deliveries by completion date, failed attempts by attempt date.</p>
            </div>
            <DispatcherLink className={styles.textLink} href={dispatcherPath('reports', preview)}>Reports</DispatcherLink>
          </header>
          {data && <DeliveriesChart days={data.week.perDay} />}
        </section>

        <section className={styles.panel}>
          <header className={styles.panelHeader}><h2>Success and failure</h2></header>
          <dl className={styles.summaryList}>
            <dt>Delivered</dt><dd>{counts?.DELIVERED ?? '–'}</dd>
            <dt>Failed</dt><dd>{counts?.FAILED ?? '–'}</dd>
            <dt>Success rate</dt><dd>{formatPercent(rate)}</dd>
            <dt>In progress</dt><dd>{inProgress ?? '–'}</dd>
          </dl>
          {rate !== null && (
            <div className={styles.splitBar} role="img" aria-label={`${formatPercent(rate)} delivered`}>
              <span className={styles.chartDelivered} style={{ width: `${rate * 100}%` }} />
              <span className={styles.chartFailed} style={{ width: `${(1 - rate) * 100}%` }} />
            </div>
          )}
          <p className={styles.panelNote}>
            Success rate = delivered ÷ (delivered + failed), over every delivery now in one of those statuses. Parcels still in progress are left out.
          </p>
        </section>
      </div>

      {data && data.pending.length > 0 && (
        <div className={styles.banner} role="status">
          <DispatcherIcon name="alert" />
          <div>
            <strong>
              {data.pending.length === 1 ? '1 merchant application is' : `${data.pending.length} merchant applications are`} waiting for review
            </strong>
            <span>{data.pending.map((m) => m.businessName).join(', ')}</span>
          </div>
          <DispatcherLink className={styles.secondaryButton} href={dispatcherPath('merchants', preview)}>Review</DispatcherLink>
        </div>
      )}
    </>
  );
}
