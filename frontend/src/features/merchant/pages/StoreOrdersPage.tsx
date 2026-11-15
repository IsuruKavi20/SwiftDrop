import { useEffect, useState } from 'react';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import {
  getMerchantDeliveries,
  getMerchantDelivery,
  type DeliveryStatus,
  type MerchantDeliveryDetail,
  type MerchantDeliverySummary,
  type PageResponse,
} from '../api/merchantOrdersApi';
import { MerchantApiError } from '../api/merchantApi';
import styles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';
import extra from './StoreOrdersPage.module.css';

const columns = ['Tracking #', 'Recipient', 'Destination', 'Type', 'Status', 'Date', 'Actions'];

const STATUS_FILTERS: Array<{ label: string; value: DeliveryStatus | 'ALL' }> = [
  { label: 'All', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'Picked Up', value: 'PICKED_UP' },
  { label: 'In Transit', value: 'IN_TRANSIT' },
  { label: 'Out for Delivery', value: 'OUT_FOR_DELIVERY' },
  { label: 'Delivered', value: 'DELIVERED' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Cancelled', value: 'CANCELLED' },
];

function statusBadgeClass(status: DeliveryStatus): string {
  switch (status) {
    case 'DELIVERED':
      return extra.statusSuccess;
    case 'FAILED':
      return extra.statusError;
    case 'CONFIRMED':
    case 'PICKED_UP':
    case 'IN_TRANSIT':
    case 'OUT_FOR_DELIVERY':
      return extra.statusInfo;
    default:
      return extra.statusNeutral;
  }
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  } catch {
    return iso;
  }
}

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

export function StoreOrdersPage({ preview }: { preview: boolean }) {
  const [statusFilter, setStatusFilter] = useState<DeliveryStatus | 'ALL'>('ALL');
  const [currentPage, setCurrentPage] = useState(0);
  const [deliveries, setDeliveries] = useState<PageResponse<MerchantDeliverySummary> | null>(null);
  const [loading, setLoading] = useState(!preview);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (preview) return;

    let active = true;

    getMerchantDeliveries(statusFilter, currentPage, 10)
      .then((res) => {
        if (active) {
          setDeliveries(res);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(
            err instanceof MerchantApiError
              ? err.message
              : 'Failed to load store deliveries. Please try again.'
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [statusFilter, currentPage, preview, refreshKey]);

  function handleFilterChange(filter: DeliveryStatus | 'ALL') {
    setStatusFilter(filter);
    setCurrentPage(0);
    setLoading(true);
  }

  function handleRetryOrders() {
    setLoading(true);
    setError(null);
    setRefreshKey((k) => k + 1);
  }

  return (
    <section className={styles.orders} aria-labelledby="orders-title">
      <div className={styles.heading}>
        <div>
          <h2 id="orders-title">Store Order History</h2>
          <p id="orders-availability">
            Manage, filter, and monitor all courier delivery shipments created for your store.
          </p>
        </div>
        <MerchantLink className={shared.button} href={merchantPath('create-order', preview)}>
          + Create Shipment
        </MerchantLink>
      </div>

      {/* Filter Bar */}
      <div className={extra.filterBar} role="group" aria-label="Order status filters">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={`${extra.filterBtn} ${statusFilter === f.value ? extra.filterBtnActive : ''}`}
            onClick={() => handleFilterChange(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && (
        <div className={shared.state} style={{ marginBottom: '1rem' }}>
          <p className={shared.error}>{error}</p>
          <button type="button" className={shared.button} onClick={handleRetryOrders}>
            Retry
          </button>
        </div>
      )}

      <div className={styles.tableRegion} role="region" aria-label="Store orders table" tabIndex={0}>
        <table className={styles.table} aria-labelledby="orders-title">
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={columns.length} className={styles.empty}>
                  <p>Loading deliveries…</p>
                </td>
              </tr>
            ) : preview ? (
              <tr>
                <td colSpan={columns.length} className={styles.empty}>
                  <strong>No orders in development preview</strong>
                  <p>Your order history will appear here once connected to live backend data.</p>
                </td>
              </tr>
            ) : !deliveries || deliveries.content.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className={styles.empty}>
                  <strong>No orders found</strong>
                  <p>
                    {statusFilter === 'ALL'
                      ? 'You have not booked any shipments yet.'
                      : `No shipments found with status "${statusFilter}".`}
                  </p>
                </td>
              </tr>
            ) : (
              deliveries.content.map((order) => (
                <tr key={order.id}>
                  <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>{order.trackingNumber}</td>
                  <td>{order.receiverName || '—'}</td>
                  <td>{order.destinationCity || '—'}</td>
                  <td>{order.deliveryType}</td>
                  <td>
                    <span className={`${extra.statusBadge} ${statusBadgeClass(order.status)}`}>
                      {order.status}
                    </span>
                  </td>
                  <td>{formatDate(order.createdAt)}</td>
                  <td>
                    <MerchantLink
                      className={shared.button}
                      style={{ minHeight: '2rem', padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                      href={merchantPath('orders', preview) + `/${order.id}`}
                    >
                      View Details
                    </MerchantLink>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {deliveries && deliveries.totalPages > 1 && (
        <div className={extra.pagination}>
          <button
            type="button"
            className={extra.pageBtn}
            disabled={deliveries.page <= 0 || loading}
            onClick={() => {
              setLoading(true);
              setCurrentPage((p) => Math.max(0, p - 1));
            }}
          >
            ← Previous
          </button>
          <span className={extra.pageInfo}>
            Page {deliveries.page + 1} of {deliveries.totalPages} ({deliveries.totalElements} total)
          </span>
          <button
            type="button"
            className={extra.pageBtn}
            disabled={deliveries.page >= deliveries.totalPages - 1 || loading}
            onClick={() => {
              setLoading(true);
              setCurrentPage((p) => p + 1);
            }}
          >
            Next →
          </button>
        </div>
      )}

      <p style={{ marginTop: '1.25rem' }}>
        Have a tracking number? <MerchantLink href={merchantPath('tracking', preview)}>Look up its public status</MerchantLink>.
      </p>
    </section>
  );
}

export function OrderDetailsPage({ orderId, preview }: { orderId: string; preview: boolean }) {
  const [order, setOrder] = useState<MerchantDeliveryDetail | null>(null);
  const [loading, setLoading] = useState(!preview);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (preview) return;

    let active = true;

    getMerchantDelivery(Number(orderId))
      .then((data) => {
        if (active) {
          setOrder(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(
            err instanceof MerchantApiError
              ? err.message
              : `Unable to load order #${orderId}. It may not exist or belong to your store.`
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [orderId, preview, refreshKey]);

  function handleRetryDetail() {
    setLoading(true);
    setError(null);
    setRefreshKey((k) => k + 1);
  }

  if (preview) {
    return (
      <section className={shared.panel} aria-labelledby="order-details-title">
        <h2 id="order-details-title">Order #{orderId}</h2>
        <p>Order details are unavailable in development preview mode.</p>
        <div className={styles.actions}>
          <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>
            Back to Store Orders
          </MerchantLink>
        </div>
      </section>
    );
  }

  if (loading) {
    return (
      <section className={shared.panel}>
        <h2>Loading order details…</h2>
        <p>Retrieving shipment data from SwiftDrop network.</p>
      </section>
    );
  }

  if (error || !order) {
    return (
      <section className={shared.panel}>
        <h2>Error Loading Order</h2>
        <p className={shared.error}>{error || 'Order not found.'}</p>
        <div className={styles.actions}>
          <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>
            Back to Store Orders
          </MerchantLink>
          <button type="button" className={shared.button} onClick={handleRetryDetail}>
            Retry
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className={shared.panel} aria-labelledby="order-details-title">
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
        <div>
          <h2 id="order-details-title" style={{ fontFamily: 'monospace', fontSize: '1.4rem' }}>
            {order.trackingNumber}
          </h2>
          <p style={{ margin: '0.25rem 0' }}>
            Booked on {formatDateTime(order.createdAt)} • Type: <strong>{order.deliveryType}</strong>
          </p>
        </div>
        <div>
          <span className={`${extra.statusBadge} ${statusBadgeClass(order.status)}`} style={{ fontSize: '0.9rem', padding: '0.35rem 0.8rem' }}>
            {order.status}
          </span>
        </div>
      </div>

      <div className={extra.detailsGrid}>
        {/* Pickup Card */}
        <div className={extra.detailCard}>
          <h3 className={extra.detailCardTitle}>Pickup Location</h3>
          <ul className={extra.detailList}>
            <li><strong>Contact:</strong> {order.pickup.contactName}</li>
            <li><strong>Phone:</strong> {order.pickup.contactPhone}</li>
            <li><strong>Street:</strong> {order.pickup.street}</li>
            <li><strong>City / District:</strong> {order.pickup.city}, {order.pickup.district}</li>
            {order.pickup.postalCode && <li><strong>Postal Code:</strong> {order.pickup.postalCode}</li>}
          </ul>
        </div>

        {/* Destination Card */}
        <div className={extra.detailCard}>
          <h3 className={extra.detailCardTitle}>Delivery Destination</h3>
          <ul className={extra.detailList}>
            <li><strong>Recipient:</strong> {order.destination.contactName}</li>
            <li><strong>Phone:</strong> {order.destination.contactPhone}</li>
            <li><strong>Street:</strong> {order.destination.street}</li>
            <li><strong>City / District:</strong> {order.destination.city}, {order.destination.district}</li>
            {order.destination.postalCode && <li><strong>Postal Code:</strong> {order.destination.postalCode}</li>}
          </ul>
        </div>
      </div>

      {order.specialInstructions && (
        <div className={shared.state} style={{ marginBottom: '1.5rem' }}>
          <h4>Special Instructions</h4>
          <p>{order.specialInstructions}</p>
        </div>
      )}

      {/* Parcels Table */}
      <h3>Parcels in Shipment ({order.parcels.length})</h3>
      <div className={extra.previewTableWrapper} style={{ marginBottom: '1.5rem' }}>
        <table className={extra.previewTable}>
          <thead>
            <tr>
              <th>Description</th>
              <th>Weight</th>
              <th>Dimensions (L×W×H)</th>
              <th>Declared Value</th>
            </tr>
          </thead>
          <tbody>
            {order.parcels.map((parcel, idx) => (
              <tr key={parcel.id ?? idx}>
                <td>{parcel.description}</td>
                <td>{parcel.weightKg} kg</td>
                <td>{parcel.lengthCm} × {parcel.widthCm} × {parcel.heightCm} cm</td>
                <td>{parcel.declaredValue != null ? `LKR ${parcel.declaredValue.toFixed(2)}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Tracking Events Timeline */}
      <h3>Tracking History</h3>
      {order.trackingEvents && order.trackingEvents.length > 0 ? (
        <div style={{ marginTop: '1rem', paddingLeft: '0.5rem' }}>
          {order.trackingEvents.map((evt, idx) => (
            <div key={evt.id ?? idx} className={extra.timelineItem}>
              <div className={extra.timelineDot} />
              <div style={{ fontWeight: 600, color: 'var(--merchant-text)' }}>
                {evt.description || evt.status}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--merchant-secondary)', marginTop: '0.2rem' }}>
                {formatDateTime(evt.eventTime)}
                {evt.locationName && ` • Location: ${evt.locationName}`}
                {evt.recordedByName && ` • By: ${evt.recordedByName}`}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p style={{ color: 'var(--merchant-secondary)' }}>No tracking events recorded yet.</p>
      )}

      <div className={styles.actions} style={{ marginTop: '2rem' }}>
        <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>
          Back to Store Orders
        </MerchantLink>
        <MerchantLink className={shared.button} href={merchantPath('create-order', preview)}>
          + Book Another Shipment
        </MerchantLink>
      </div>
    </section>
  );
}
