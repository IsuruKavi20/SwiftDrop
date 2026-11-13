import { MerchantIcon, type MerchantIconName } from '../components/MerchantIcon';
import { MerchantLink } from '../components/MerchantLink';
import { ParcelIllustration } from '../components/ParcelIllustration';
import { merchantPath } from '../merchantRoutes';
import styles from './MerchantDashboardPage.module.css';

const metrics: { label: string; icon: MerchantIconName; tone: string }[] = [
  { label: 'Total Orders', icon: 'box', tone: 'orange' },
  { label: 'Pending', icon: 'cart', tone: 'amber' },
  { label: 'In Transit', icon: 'truck', tone: 'blue' },
  { label: 'Delivered', icon: 'completed', tone: 'green' },
];

export function MerchantDashboardPage({ preview }: { preview: boolean }) {
  return (
    <div className={styles.dashboard}>
      <section className={styles.welcome} aria-labelledby="merchant-welcome">
        <div>
          <span className={styles.eyebrow}>SwiftDrop Merchant</span>
          <h2 id="merchant-welcome">Your store deliveries, in one place</h2>
          <p>Set up your business profile, find tracking information, and see where order tools will be available.</p>
        </div>
        <ParcelIllustration />
      </section>

      <ul className={styles.metrics} aria-label="Delivery metrics">
        {metrics.map(({ label, icon, tone }) => (
          <li className={styles.metric} key={label}>
            <span className={styles.metricIcon} data-tone={tone}><MerchantIcon name={icon} size={23} /></span>
            <div><strong>{label}</strong><small>Unavailable</small></div>
          </li>
        ))}
      </ul>

      <div className={styles.contentGrid}>
        <section className={styles.panel}>
          <h2>Recent Store Orders</h2>
          <p>Recent orders will appear here when Merchant order history is available.</p>
          <MerchantLink className={styles.textLink} href={merchantPath('orders', preview)}>Go to Store Orders</MerchantLink>
        </section>
        <div className={styles.rightColumn}>
          <section className={styles.panel}>
            <h2>Fulfillment Summary</h2>
            <p>Fulfillment totals are unavailable until Merchant delivery reporting is connected.</p>
          </section>
          <section className={styles.panel}>
            <h2>Recent Updates</h2>
            <p>Merchant activity is unavailable. If you have a tracking number, you can look up its public status.</p>
            <MerchantLink className={styles.textLink} href={merchantPath('tracking', preview)}>Go to Tracking</MerchantLink>
          </section>
        </div>
      </div>
    </div>
  );
}
