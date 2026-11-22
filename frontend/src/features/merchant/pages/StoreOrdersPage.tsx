import { useEffect, useState } from 'react';
import { MerchantIcon } from '../components/MerchantIcon';
import { ShipmentTimeline } from '../components/ShipmentTimeline';
import { ConfirmMerchantOrder } from '../components/ConfirmMerchantOrder';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import {
  getMerchantDeliveries,
  getMerchantDelivery,
  formatMerchantFee,
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

const serviceLabels = { STANDARD: 'Standard', EXPRESS: 'Express', SAME_DAY: 'Same day' };

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
      timeZone: /^\d{4}-\d{2}-\d{2}$/.test(iso) ? 'UTC' : undefined,
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

function orderListLocation() {
  const params = new URLSearchParams(window.location.search);
  const status = STATUS_FILTERS.find(({ value }) => value === params.get('status'))?.value ?? 'ALL';
  const page = Number(params.get('page'));
  return { status, page: Number.isSafeInteger(page) && page > 0 && page <= 2147483647 ? page : 0 };
}

function orderListQuery(status: DeliveryStatus | 'ALL', page: number) {
  const params = new URLSearchParams();
  if (status !== 'ALL') params.set('status', status);
  if (page > 0) params.set('page', String(page));
  return params.size ? `?${params}` : '';
}

export function StoreOrdersPage({ preview }: { preview: boolean }) {
  const [statusFilter, setStatusFilter] = useState(() => orderListLocation().status);
  const [currentPage, setCurrentPage] = useState(() => orderListLocation().page);
  const [deliveries, setDeliveries] = useState<PageResponse<MerchantDeliverySummary> | null>(null);
  const [loading, setLoading] = useState(!preview);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const listQuery = orderListQuery(statusFilter, currentPage);

  function updateListLocation(status: DeliveryStatus | 'ALL', page: number) {
    window.history.replaceState(window.history.state, '', window.location.pathname + orderListQuery(status, page));
  }

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
    if (filter === statusFilter && currentPage === 0) return;
    setStatusFilter(filter);
    setCurrentPage(0);
    updateListLocation(filter, 0);
    setError(null);
    setLoading(!preview);
  }

  function handleRetryOrders() {
    setLoading(true);
    setError(null);
    setRefreshKey((k) => k + 1);
  }

  return (
    <section className={extra.history} aria-labelledby="orders-title">
      <div className={styles.heading}>
        <div>
          <h2 id="orders-title">Store Order History</h2>
          <p id="orders-availability">
            Review shipments booked by your store and follow their delivery status.
          </p>
        </div>
        <MerchantLink className={styles.primaryButton} href={merchantPath('create-order', preview)}>
          <MerchantIcon name="plus" size={17} /> Create Shipment
        </MerchantLink>
      </div>

      <div className={extra.filterBar} role="group" aria-label="Order status filters">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={extra.filterBtn}
            aria-pressed={statusFilter === f.value}
            onClick={() => handleFilterChange(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className={extra.listState} role="status">
          <span className={extra.spinner} aria-hidden="true" />
          <strong>Loading deliveries…</strong>
          <p>Retrieving your store’s shipments.</p>
        </div>
      ) : error ? (
        <div className={extra.listState}>
          <div role="alert">
            <h3>Couldn’t load orders</h3>
            <p>{error}</p>
          </div>
          <button type="button" className={extra.pageBtn} onClick={handleRetryOrders}>Retry</button>
        </div>
      ) : preview || !deliveries?.content.length ? (
        <div className={extra.listState}>
          <span className={extra.stateIcon}><MerchantIcon name="box" size={24} /></span>
          <h3>{preview ? 'No orders in development preview' : 'No orders found'}</h3>
          <p>{preview ? 'Sign in as a Merchant to see your order history.'
            : statusFilter === 'ALL' ? 'Shipments will appear here after you book them.'
              : `There are no shipments with the ${STATUS_FILTERS.find(({ value }) => value === statusFilter)?.label} status.`}</p>
          {!preview && (
            statusFilter === 'ALL' ? (
              currentPage > 0
                ? <button type="button" className={extra.pageBtn} onClick={() => handleFilterChange('ALL')}>Back to first page</button>
                : <MerchantLink className={styles.primaryButton} href={merchantPath('create-order', preview)}>Create Shipment</MerchantLink>
            ) : <button type="button" className={extra.pageBtn} onClick={() => handleFilterChange('ALL')}>Clear status filter</button>
          )}
        </div>
      ) : (
        <div className={extra.tableRegion} role="region" aria-label="Store orders table" tabIndex={0}>
          <table className={extra.historyTable} aria-labelledby="orders-title">
            <thead><tr>{columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr></thead>
            <tbody>
              {deliveries.content.map((order) => (
                <tr key={order.id}>
                  <td className={extra.trackingNumber}>{order.trackingNumber}</td>
                  <td>{order.receiverName || '—'}</td>
                  <td>{order.destinationCity || '—'}</td>
                  <td>{serviceLabels[order.deliveryType]}</td>
                  <td>
                    <span className={`${extra.statusBadge} ${statusBadgeClass(order.status)}`}>
                      {STATUS_FILTERS.find(({ value }) => value === order.status)?.label ?? order.status}
                    </span>
                  </td>
                  <td>{formatDate(order.createdAt)}</td>
                  <td>
                    <MerchantLink
                      className={extra.detailsLink}
                      href={merchantPath('orders', preview) + `/${order.id}` + listQuery}
                    >
                      View Details
                      <MerchantIcon name="right" size={15} />
                    </MerchantLink>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && !error && deliveries && deliveries.totalPages > 1 && (
        <nav className={extra.pagination} aria-label="Store order pages">
          <button
            type="button"
            className={extra.pageBtn}
            disabled={deliveries.page <= 0 || loading}
            onClick={() => {
              setLoading(true);
              updateListLocation(statusFilter, Math.max(0, currentPage - 1));
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
              updateListLocation(statusFilter, currentPage + 1);
              setCurrentPage((p) => p + 1);
            }}
          >
            Next →
          </button>
        </nav>
      )}

      <p className={extra.trackingHelp}>
        <MerchantIcon name="pin" size={18} />
        <span>Have a tracking number? <MerchantLink href={merchantPath('tracking', preview)}>Look up its public status</MerchantLink>.</span>
      </p>
    </section>
  );
}

export function OrderDetailsPage({ orderId, preview }: { orderId: string; preview: boolean }) {
  const listLocation = orderListLocation();
  const backToOrders = merchantPath('orders', preview) + orderListQuery(listLocation.status, listLocation.page);
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
          <MerchantLink className={shared.button} href={backToOrders}>
            Back to Store Orders
          </MerchantLink>
        </div>
      </section>
    );
  }

  if (loading) {
    return (
      <section className={extra.listState} role="status">
        <span className={extra.spinner} aria-hidden="true" />
        <h2>Loading order details…</h2>
        <p>Retrieving shipment data from SwiftDrop network.</p>
      </section>
    );
  }

  if (error || !order) {
    return (
      <section className={extra.listState}>
        <div role="alert">
          <h2>Error Loading Order</h2>
          <p className={shared.error}>{error || 'Order not found.'}</p>
        </div>
        <div className={styles.actions}>
          <MerchantLink className={shared.button} href={backToOrders}>
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
    <section className={extra.shipment} aria-labelledby="order-details-title">
      <div className={extra.shipmentSummary}>
        <h2 id="order-details-title">Tracking No: <span className={extra.trackingNumber}>{order.trackingNumber}</span></h2>
        <span className={`${extra.statusBadge} ${statusBadgeClass(order.status)}`}>
          {STATUS_FILTERS.find(({ value }) => value === order.status)?.label ?? order.status}
        </span>
        <dl className={extra.shipmentFacts}>
          <div><dt>Delivery service</dt><dd>{serviceLabels[order.deliveryType] ?? order.deliveryType}</dd></div>
          <div><dt>Booked</dt><dd>{formatDateTime(order.createdAt)}</dd></div>
          <div><dt>Estimated delivery</dt><dd>{formatDate(order.estimatedDeliveryDate)}</dd></div>
          <div><dt>Estimated delivery fee</dt><dd>{order.estimatedFee == null ? 'Not recorded' : formatMerchantFee(order.estimatedFee)}</dd></div>
          {order.deliveredAt && <div><dt>Delivered</dt><dd>{formatDateTime(order.deliveredAt)}</dd></div>}
        </dl>
        {order.status === 'PENDING' && (
          <p className={extra.confirmationGuidance}>Ready to dispatch? Review the details and click Confirm Order to proceed.</p>
        )}
        <ConfirmMerchantOrder order={order} onConfirmed={setOrder} />
      </div>

      <div className={extra.detailsGrid}>
        {/* Pickup Card */}
        <div className={extra.detailCard}>
          <h3 className={extra.detailCardTitle}><MerchantIcon name="pin" size={19} /> Pickup Location</h3>
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
          <h3 className={extra.detailCardTitle}><MerchantIcon name="truck" size={19} /> Delivery Destination</h3>
          <ul className={extra.detailList}>
            <li><strong>Recipient:</strong> {order.destination.contactName}</li>
            <li><strong>Phone:</strong> {order.destination.contactPhone}</li>
            <li><strong>Recipient Email:</strong> {order.recipientEmail ?? 'Not provided'}</li>
            <li><strong>Street:</strong> {order.destination.street}</li>
            <li><strong>City / District:</strong> {order.destination.city}, {order.destination.district}</li>
            {order.destination.postalCode && <li><strong>Postal Code:</strong> {order.destination.postalCode}</li>}
          </ul>
        </div>
      </div>

      {order.specialInstructions && (
        <section className={extra.detailCard}>
          <h3 className={extra.detailCardTitle}>Special Instructions</h3>
          <p>{order.specialInstructions}</p>
        </section>
      )}

      {/* Parcels Table */}
      <section className={extra.detailCard}>
        <h3 className={extra.detailCardTitle}>Parcels in Shipment ({order.parcels.length})</h3>
        <div className={extra.parcelRegion} role="region" aria-label="Shipment parcels" tabIndex={0}>
          <table className={extra.parcelTable}>
            <thead>
              <tr>
                <th scope="col">Description</th>
                <th scope="col">Weight</th>
                <th scope="col">Dimensions (L×W×H)</th>
                <th scope="col">Declared Value</th>
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
      </section>

      {/* Tracking Events Timeline */}
      <section className={extra.detailCard}>
        <h3 className={extra.detailCardTitle}>Tracking History</h3>
        <ShipmentTimeline events={order.trackingEvents ?? []} recordedByLabel="By:" />
      </section>

      <div className={styles.actions}>
        <MerchantLink className={shared.button} href={backToOrders}>
          Back to Store Orders
        </MerchantLink>
        <MerchantLink className={shared.button} href={merchantPath('create-order', preview)}>
          + Book Another Shipment
        </MerchantLink>
      </div>
    </section>
  );
}
