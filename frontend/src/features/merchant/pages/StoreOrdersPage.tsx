import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import styles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';

const columns = ['Order ID', 'Recipient', 'Destination', 'Status', 'Created', 'Actions'];

export function StoreOrdersPage({ preview }: { preview: boolean }) {
  return (
    <section className={styles.orders} aria-labelledby="orders-title">
      <div className={styles.heading}>
        <div>
          <h2 id="orders-title">Order history</h2>
          <p id="orders-availability">Search, status filters, and pagination require the Merchant order list API.</p>
        </div>
        <MerchantLink className={shared.button} href={merchantPath('create-order', preview)}>Create Order</MerchantLink>
      </div>
      <div className={styles.tableRegion} role="region" aria-label="Store orders table" tabIndex={0}>
        <table className={styles.table} aria-labelledby="orders-title">
          <thead><tr>{columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr></thead>
          <tbody><tr><td colSpan={columns.length} className={styles.empty}>
            <strong>{preview ? 'No orders in development preview' : 'Merchant orders are unavailable'}</strong>
            <p>{preview ? 'Your order history will appear here once Merchant delivery access is connected.'
              : 'The backend currently provides delivery history for Customer accounts only.'}</p>
          </td></tr></tbody>
        </table>
      </div>
      <p>Have a tracking number? <MerchantLink href={merchantPath('tracking', preview)}>Look up its public status</MerchantLink>.</p>
    </section>
  );
}

export function OrderDetailsPage({ orderId, preview }: { orderId: string; preview: boolean }) {
  return (
    <section className={shared.panel} aria-labelledby="order-details-title">
      <h2 id="order-details-title">Order {orderId}</h2>
      <p>{preview ? 'Order details are unavailable in development preview.'
        : 'The Merchant order details API is unavailable. Order information and private tracking history cannot be loaded.'}</p>
      <p>Public tracking is available if you have the delivery’s tracking number.</p>
      <div className={styles.actions}>
        <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>Back to Store Orders</MerchantLink>
        <MerchantLink className={shared.button} href={merchantPath('tracking', preview)}>Go to Tracking</MerchantLink>
      </div>
    </section>
  );
}
