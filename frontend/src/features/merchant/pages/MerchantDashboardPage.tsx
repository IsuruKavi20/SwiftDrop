import { useCallback, useEffect, useRef, useState } from 'react';
import { navigate, routes } from '../../../app/router';
import { logOut } from '../../auth/api/authApi';
import { Alert } from '../../auth/components/FormControls';
import { claimsWithFreshRoles } from '../../auth/lib/freshClaims';
import type { AccessClaims } from '../../auth/lib/session';
import {
  getMerchantDashboard,
  getMerchantDeliveries,
  type DeliveryStatus,
  type MerchantDashboardData,
  type MerchantDeliverySummary,
  type PageResponse,
} from '../api/merchantOrdersApi';
import { MerchantApiError } from '../api/merchantApi';
import { OrderDetailsModal } from '../components/OrderDetailsModal';
import styles from '../components/MerchantDashboard.module.css';
import previewStyles from './MerchantDashboardPage.module.css';
import ordersStyles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';
import extra from './StoreOrdersPage.module.css';
import { MerchantIcon, type MerchantIconName } from '../components/MerchantIcon';
import { MerchantLink } from '../components/MerchantLink';
import { ParcelIllustration } from '../components/ParcelIllustration';
import { merchantPath } from '../merchantRoutes';

type PageState =
  | { kind: 'loading' }
  | { kind: 'ready'; dashboard: MerchantDashboardData; claims: AccessClaims }
  | { kind: 'accessDenied'; message: string }
  | { kind: 'error'; message: string };

const STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

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

const FILTER_OPTIONS = [
  { label: 'All', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'In Transit', value: 'IN_TRANSIT' },
  { label: 'Delivered', value: 'DELIVERED' },
] as const;

type DashboardFilter = (typeof FILTER_OPTIONS)[number]['value'];

const ORDERS_PAGE_SIZE = 10;
const TRANSIT_STATUSES = ['CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'] as const;
const completionFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

function sortableTimestamp(timestamp: string) {
  return timestamp.replace(/(?:\.(\d+))?Z$/, (_, fraction = '') => `.${fraction.padEnd(9, '0')}Z`);
}

async function getDashboardOrders(filter: DashboardFilter, page: number): Promise<PageResponse<MerchantDeliverySummary>> {
  if (filter !== 'IN_TRANSIT') return getMerchantDeliveries(filter, page, ORDERS_PAGE_SIZE);

  // Each status is already sorted by creation time and ID. Only its prefix can
  // contribute to the requested page of the combined result.
  const end = (page + 1) * ORDERS_PAGE_SIZE;
  const batchSize = Math.min(end, 50);
  const results = await Promise.all(TRANSIT_STATUSES.map(async (status) => {
    const first = await getMerchantDeliveries(status, 0, batchSize);
    const batches = Math.ceil(Math.min(end, first.totalElements) / batchSize);
    const remaining = await Promise.all(Array.from(
      { length: Math.max(0, batches - 1) },
      (_, index) => getMerchantDeliveries(status, index + 1, batchSize),
    ));
    return { total: first.totalElements, orders: [first, ...remaining].flatMap((batch) => batch.content) };
  }));
  const totalElements = results.reduce((total, result) => total + result.total, 0);
  const orders = results.flatMap((result) => result.orders.map((order) => ({
    order,
    timestamp: sortableTimestamp(order.createdAt),
  })));
  orders.sort((left, right) => {
    if (left.timestamp === right.timestamp) return right.order.id - left.order.id;
    return left.timestamp < right.timestamp ? 1 : -1;
  });

  return {
    content: orders.slice(page * ORDERS_PAGE_SIZE, end).map(({ order }) => order),
    page,
    size: ORDERS_PAGE_SIZE,
    totalElements,
    totalPages: Math.ceil(totalElements / ORDERS_PAGE_SIZE),
  };
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

// Always start by loading: a missing merchant role is only final after a fresh token (see fetchDashboardData)
function getInitialPageState(): PageState {
  return { kind: 'loading' };
}

async function fetchDashboardData(): Promise<{
  dashboard: MerchantDashboardData;
  deliveries: PageResponse<MerchantDeliverySummary>;
  claims: AccessClaims;
}> {
  // A just-approved merchant still holds the old token: claimsWithFreshRoles refreshes once before saying no
  const claims = await claimsWithFreshRoles('merchant:api-access');
  if (!claims) {
    throw new MerchantApiError(401, 'Session expired');
  }
  const permissions = claims.entitlements ?? [];
  if (!permissions.includes('merchant:api-access')) {
    throw new MerchantApiError(
      403,
      'You need merchant privileges to access this dashboard. Please sign in with an authorized merchant account.'
    );
  }

  const [dashboard, deliveries] = await Promise.all([
    getMerchantDashboard(),
    getMerchantDeliveries('ALL', 0, 10),
  ]);

  return { dashboard, deliveries, claims };
}

function RealMerchantDashboard({ claims: propClaims }: { claims?: AccessClaims } = {}) {
  const [pageState, setPageState] = useState<PageState>(() => {
    if (propClaims) {
      const permissions = propClaims.entitlements ?? [];
      if (!permissions.includes('merchant:api-access')) {
        return {
          kind: 'accessDenied',
          message:
            'You need merchant privileges to access this dashboard. Please sign in with an authorized merchant account.',
        };
      }
    }
    return getInitialPageState();
  });
  const [deliveries, setDeliveries] = useState<PageResponse<MerchantDeliverySummary> | null>(null);
  const [loadingOrders, setLoadingOrders] = useState<boolean>(false);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [totalsError, setTotalsError] = useState<string | null>(null);
  const ordersRequest = useRef(0);
  const [statusFilter, setStatusFilter] = useState<DashboardFilter>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [selectedDeliveryId, setSelectedDeliveryId] = useState<number | null>(null);

  const fetchOrders = useCallback(async (filter: DashboardFilter, page: number) => {
    const request = ++ordersRequest.current;
    setLoadingOrders(true);
    setOrdersError(null);
    try {
      let result = await getDashboardOrders(filter, page);
      if (request !== ordersRequest.current) return;
      const lastPage = Math.max(0, result.totalPages - 1);
      if (page > lastPage) result = await getDashboardOrders(filter, lastPage);
      if (request === ordersRequest.current) {
        setDeliveries(result);
        setCurrentPage(result.page);
      }
    } catch (error) {
      if (request === ordersRequest.current) {
        setOrdersError(error instanceof Error ? error.message : 'Could not load store deliveries.');
      }
    } finally {
      if (request === ordersRequest.current) setLoadingOrders(false);
    }
  }, []);

  const loadDashboard = useCallback(() => {
    let active = true;

    fetchDashboardData()
      .then(({ dashboard, deliveries: deliveriesPage, claims }) => {
        if (active) {
          setPageState({ kind: 'ready', dashboard, claims: propClaims || claims });
          setDeliveries(deliveriesPage);
        }
      })
      .catch((err) => {
        if (!active) return;
        if (err instanceof MerchantApiError && err.status === 401) {
          navigate(routes.login);
        } else if (err instanceof MerchantApiError && err.status === 403) {
          setPageState({
            kind: 'accessDenied',
            message:
              err.message ||
              'You need merchant privileges to access this dashboard. Please sign in with an authorized merchant account.',
          });
        } else {
          setPageState({
            kind: 'error',
            message:
              err instanceof Error ? err.message : 'Could not load your merchant dashboard.',
          });
        }
      });

    return () => {
      active = false;
    };
  }, [propClaims]);

  useEffect(() => loadDashboard(), [loadDashboard]);

  function handleFilterChange(newFilter: DashboardFilter) {
    setStatusFilter(newFilter);
    setCurrentPage(0);
    void fetchOrders(newFilter, 0);
  }

  function handlePageChange(newPage: number) {
    setCurrentPage(newPage);
    void fetchOrders(statusFilter, newPage);
  }

  function handleRetry() {
    setPageState({ kind: 'loading' });
    loadDashboard();
  }

  async function refreshTotals() {
    setTotalsError(null);
    try {
      const dashboard = await getMerchantDashboard();
      setPageState((previous) => previous.kind === 'ready' ? { ...previous, dashboard } : previous);
    } catch {
      setTotalsError('Could not refresh dashboard totals. Your order confirmation has been saved.');
    }
  }

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  if (pageState.kind === 'loading') {
    return (
      <div className={extra.listState} role="status">
        <span className={extra.spinner} aria-hidden="true" />
        <strong>Loading merchant dashboard…</strong>
      </div>
    );
  }

  if (pageState.kind === 'accessDenied') {
    return (
      <div className={extra.listState}>
        <h2>Access Denied</h2>
        <Alert kind="error">{pageState.message}</Alert>
        <div className={ordersStyles.actions}>
          <button
            type="button"
            className={shared.button}
            onClick={() => navigate(routes.account)}
          >
            Go to Account
          </button>
          <button
            type="button"
            className={shared.button}
            onClick={() => void onLogOut()}
          >
            Sign in with another account
          </button>
        </div>
      </div>
    );
  }

  if (pageState.kind === 'error') {
    return (
      <div className={extra.listState}>
        <h2>Error Loading Dashboard</h2>
        <Alert kind="error">{pageState.message}</Alert>
        <div className={ordersStyles.actions}>
          <button
            type="button"
            className={shared.button}
            onClick={handleRetry}
          >
            Retry
          </button>
          <button
            type="button"
            className={shared.button}
            onClick={() => navigate(routes.account)}
          >
            Go to Account
          </button>
        </div>
      </div>
    );
  }

  const { dashboard } = pageState;
  const tradingName = dashboard.businessName || 'Merchant Portal';
  const totalSubmitted = dashboard.totalOrders ?? 0;
  const delivered = dashboard.deliveredOrders ?? 0;
  const completion = totalSubmitted ? delivered / totalSubmitted * 100 : 0;
  const fulfillment = [
    { label: 'Total Submitted', tone: 'orange', count: totalSubmitted },
    { label: 'In Transit', tone: 'blue', count: dashboard.inTransitOrders ?? 0 },
    { label: 'Delivered', tone: 'green', count: delivered },
    { label: 'Failed/Cancelled', tone: 'red', count: (dashboard.countsByStatus?.FAILED ?? 0) + (dashboard.countsByStatus?.CANCELLED ?? 0) },
  ];

  const metricCards: { label: string; icon: MerchantIconName; tone: string; count: number }[] = [
    { label: 'Total Orders', icon: 'box', tone: 'orange', count: dashboard.totalOrders ?? 0 },
    { label: 'Pending', icon: 'cart', tone: 'amber', count: dashboard.pendingOrders ?? 0 },
    { label: 'In Transit', icon: 'truck', tone: 'blue', count: dashboard.inTransitOrders ?? 0 },
    { label: 'Delivered', icon: 'completed', tone: 'green', count: dashboard.deliveredOrders ?? 0 },
  ];

  return (
    <div className={previewStyles.dashboard}>
      {/* Profile Completion Warning Banner */}
      {!dashboard.profileComplete && (
        <div className={previewStyles.profileWarningBanner} role="alert">
          <div className={previewStyles.bannerContent}>
            <span className={previewStyles.metricIcon} data-tone="amber"><MerchantIcon name="box" /></span>
            <div>
              <span className={previewStyles.bannerTitle}>Complete your merchant business profile</span>
              <p className={previewStyles.bannerText}>
                Your merchant business profile is missing required address or contact details.
                Complete it to enable full dispatch services.
              </p>
            </div>
          </div>
          <button
            type="button"
            className={`${shared.button} ${previewStyles.bannerAction}`}
            onClick={() => navigate(merchantPath('business-profile', false))}
          >
            Complete Profile →
          </button>
        </div>
      )}

      {/* Welcome / Business Name Banner */}
      <section className={previewStyles.welcome} aria-labelledby="merchant-welcome">
        <div>
          <span className={previewStyles.eyebrow}>SwiftDrop Merchant</span>
          <h2 id="merchant-welcome">• {tradingName}</h2>
          <p>Review your shipments and track how they’re moving through delivery.</p>
          <div className={previewStyles.welcomeActions}>
            <button
              type="button"
              className={shared.button}
              onClick={() => navigate(routes.merchantProfile)}
            >
              Business Profile
            </button>
            <MerchantLink className={shared.button} href={merchantPath('orders', false)}>
              Store Orders
            </MerchantLink>
          </div>
        </div>
        <ParcelIllustration />
      </section>

      {/* Summary Statistics Cards */}
      <ul
        className={previewStyles.metrics}
        aria-label="Order summary statistics"
        role="region"
      >
        {metricCards.map(({ label, icon, tone, count }) => (
          <li className={previewStyles.metric} key={label}>
            <span className={previewStyles.metricIcon} data-tone={tone}>
              <MerchantIcon name={icon} size={23} />
            </span>
            <div>
              <span className={previewStyles.metricLabel}>{label}</span>
              <strong className={previewStyles.metricValue}>{count}</strong>
            </div>
          </li>
        ))}
      </ul>
      {totalsError && <div className={ordersStyles.actions}>
        <Alert kind="error">{totalsError}</Alert>
        <button type="button" className={shared.button} onClick={() => void refreshTotals()}>Refresh totals</button>
      </div>}

      <section className={`${previewStyles.panel} ${previewStyles.fulfillment}`} aria-labelledby="fulfillment-summary">
        <h2 id="fulfillment-summary">Fulfillment Summary</h2>
        <div className={previewStyles.fulfillmentContent}>
          <div className={previewStyles.gauge}>
            <span
              aria-hidden="true"
              className={previewStyles.gaugeRing}
              style={{ background: `conic-gradient(from 270deg, var(--merchant-accent) ${completion * 1.8}deg, var(--merchant-line) 0deg)` }}
            />
            <p className={previewStyles.gaugeValue}>
              <strong>{completionFormat.format(completion)}%</strong>
              <span>Delivery completion</span>
            </p>
          </div>
          <dl className={previewStyles.fulfillmentLegend}>
            {fulfillment.map(({ label, tone, count }) => (
              <div key={label} className={previewStyles.legendRow} data-tone={tone}>
                <dt>{label}</dt>
                <dd>{count.toLocaleString()}</dd>
              </div>
            ))}
          </dl>
        </div>
        {totalSubmitted === 0 && <p className={previewStyles.fulfillmentEmpty}>Your delivery summary will update after you book your first shipment.</p>}
      </section>

      <section className={previewStyles.panel} aria-labelledby="orders-title">
        <div className={ordersStyles.heading}>
          <div>
            <h2 id="orders-title">Recent orders</h2>
            <p>Filter store shipments by status or open an order for its tracking history.</p>
          </div>
          <MerchantLink className={ordersStyles.primaryButton} href={merchantPath('create-order', false)}>
            <MerchantIcon name="plus" size={17} /> Create Order
          </MerchantLink>
        </div>

        {/* Status Filter Buttons */}
        <div className={extra.filterBar} role="group" aria-label="Order status filters">
          {FILTER_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={extra.filterBtn}
              aria-pressed={statusFilter === opt.value}
              onClick={() => handleFilterChange(opt.value)}
            >
              {opt.label}
            </button>
          ))}
        </div>

        {/* Table / Empty State / Loading */}
        {loadingOrders ? (
          <div className={extra.listState} role="status">
            <span className={extra.spinner} aria-hidden="true" />
            <strong>Loading deliveries…</strong>
          </div>
        ) : ordersError ? (
          <div className={extra.listState}>
            <div role="alert"><h3>Couldn’t load orders</h3><p>{ordersError}</p></div>
            <button type="button" className={extra.pageBtn} onClick={() => void fetchOrders(statusFilter, currentPage)}>Retry orders</button>
          </div>
        ) : !deliveries || deliveries.content.length === 0 ? (
          <div className={extra.listState}>
            <span className={extra.stateIcon}><MerchantIcon name="box" size={24} /></span>
            {statusFilter === 'ALL' ? (
              <>
                <h3>No orders yet</h3>
                <p>
                  No orders have been placed for your store yet. Create your first delivery shipment to get started.
                </p>
                <MerchantLink className={shared.button} href={merchantPath('create-order', false)}>
                  + Create Order
                </MerchantLink>
              </>
            ) : (
              <>
                <h3>No matching orders</h3>
                <p>
                  No orders match the {FILTER_OPTIONS.find((option) => option.value === statusFilter)?.label} filter.
                </p>
                <button type="button" className={shared.button} onClick={() => handleFilterChange('ALL')}>
                  Clear Filter
                </button>
              </>
            )}
          </div>
        ) : (
          <>
            <div className={extra.tableRegion} role="region" aria-label="Store orders table" tabIndex={0}>
              <table className={extra.historyTable} aria-labelledby="orders-title">
                <thead>
                  <tr>
                    <th scope="col">Tracking Number</th>
                    <th scope="col">Recipient</th>
                    <th scope="col">Destination</th>
                    <th scope="col">Type</th>
                    <th scope="col">Status</th>
                    <th scope="col">Date</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {deliveries.content.map((delivery) => (
                    <tr
                      key={delivery.id}
                      className={styles.tableRow}
                      onClick={() => setSelectedDeliveryId(delivery.id)}
                    >
                      <td className={extra.trackingNumber}>{delivery.trackingNumber}</td>
                      <td>{delivery.receiverName || '—'}</td>
                      <td>{delivery.destinationCity || '—'}</td>
                      <td>{delivery.deliveryType}</td>
                      <td>
                        <span className={`${extra.statusBadge} ${statusBadgeClass(delivery.status)}`}>
                          {STATUS_LABELS[delivery.status] || delivery.status}
                        </span>
                      </td>
                      <td>{formatDate(delivery.createdAt)}</td>
                      <td>
                        <button
                          type="button"
                          className={extra.detailsLink}
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedDeliveryId(delivery.id);
                          }}
                        >
                          View Details
                          <MerchantIcon name="right" size={15} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className={extra.pagination}>
              <span className={extra.pageInfo}>
                Showing page {deliveries.page + 1} of {Math.max(deliveries.totalPages, 1)} ({deliveries.totalElements} orders total)
              </span>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  className={extra.pageBtn}
                  disabled={currentPage === 0}
                  onClick={() => handlePageChange(currentPage - 1)}
                >
                  ← Previous
                </button>
                <button
                  type="button"
                  className={extra.pageBtn}
                  disabled={currentPage + 1 >= deliveries.totalPages}
                  onClick={() => handlePageChange(currentPage + 1)}
                >
                  Next →
                </button>
              </div>
            </div>
          </>
        )}
      </section>

      {/* Order Details Modal */}
      {selectedDeliveryId !== null && (
        <OrderDetailsModal
          key={selectedDeliveryId}
          deliveryId={selectedDeliveryId}
          onClose={() => setSelectedDeliveryId(null)}
          onConfirmed={() => {
            void fetchOrders(statusFilter, currentPage);
            void refreshTotals();
          }}
        />
      )}
    </div>
  );
}

const metrics: { label: string; icon: MerchantIconName; tone: string }[] = [
  { label: 'Total Orders', icon: 'box', tone: 'orange' },
  { label: 'Pending', icon: 'cart', tone: 'amber' },
  { label: 'In Transit', icon: 'truck', tone: 'blue' },
  { label: 'Delivered', icon: 'completed', tone: 'green' },
];

function PreviewMerchantDashboard() {
  return (
    <div className={previewStyles.dashboard}>
      <section className={previewStyles.welcome} aria-labelledby="merchant-welcome">
        <div>
          <span className={previewStyles.eyebrow}>SwiftDrop Merchant</span>
          <h2 id="merchant-welcome">Your store deliveries, in one place</h2>
          <p>Set up your business profile, find tracking information, and see where order tools will be available.</p>
        </div>
        <ParcelIllustration />
      </section>

      <ul className={previewStyles.metrics} aria-label="Delivery metrics">
        {metrics.map(({ label, icon, tone }) => (
          <li className={previewStyles.metric} key={label}>
            <span className={previewStyles.metricIcon} data-tone={tone}>
              <MerchantIcon name={icon} size={23} />
            </span>
            <div>
              <strong>{label}</strong>
              <small>Unavailable</small>
            </div>
          </li>
        ))}
      </ul>

      <div className={previewStyles.contentGrid}>
        <section className={previewStyles.panel}>
          <h2>Recent Store Orders</h2>
          <p>Recent orders will appear here when Merchant order history is available.</p>
          <MerchantLink className={previewStyles.textLink} href={merchantPath('orders', true)}>
            Go to Store Orders
          </MerchantLink>
        </section>
        <div className={previewStyles.rightColumn}>
          <section className={previewStyles.panel}>
            <h2>Fulfillment Summary</h2>
            <p>Fulfillment totals are unavailable until Merchant delivery reporting is connected.</p>
          </section>
          <section className={previewStyles.panel}>
            <h2>Recent Updates</h2>
            <p>Merchant activity is unavailable. If you have a tracking number, you can look up its public status.</p>
            <MerchantLink className={previewStyles.textLink} href={merchantPath('tracking', true)}>
              Go to Tracking
            </MerchantLink>
          </section>
        </div>
      </div>
    </div>
  );
}

export function MerchantDashboardPage({
  preview,
  claims,
}: {
  preview?: boolean;
  claims?: AccessClaims;
} = {}) {
  if (preview) {
    return <PreviewMerchantDashboard />;
  }
  return <RealMerchantDashboard claims={claims} />;
}
