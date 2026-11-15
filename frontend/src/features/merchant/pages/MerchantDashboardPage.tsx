import { useCallback, useEffect, useState } from 'react';
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

const FILTER_OPTIONS: Array<{ label: string; value: DeliveryStatus | 'ALL' }> = [
  { label: 'All', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'In Transit', value: 'IN_TRANSIT' },
  { label: 'Delivered', value: 'DELIVERED' },
  { label: 'Cancelled', value: 'CANCELLED' },
];

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
  const [statusFilter, setStatusFilter] = useState<DeliveryStatus | 'ALL'>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [selectedDeliveryId, setSelectedDeliveryId] = useState<number | null>(null);

  const fetchOrders = useCallback(async (filter: DeliveryStatus | 'ALL', page: number) => {
    setLoadingOrders(true);
    try {
      const result = await getMerchantDeliveries(filter, page, 10);
      setDeliveries(result);
    } catch {
      // keep existing list or let user retry
    } finally {
      setLoadingOrders(false);
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

  function handleFilterChange(newFilter: DeliveryStatus | 'ALL') {
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

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  if (pageState.kind === 'loading') {
    return (
      <div style={{ textAlign: 'center', padding: '64px 20px', color: 'var(--color-text-muted)' }}>
        <p>Loading merchant dashboard…</p>
      </div>
    );
  }

  if (pageState.kind === 'accessDenied') {
    return (
      <div className={shared.state} style={{ padding: '2rem 1rem' }}>
        <h2 style={{ color: 'var(--color-error)' }}>Access Denied</h2>
        <Alert kind="error">{pageState.message}</Alert>
        <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
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
      <div className={shared.state} style={{ padding: '2rem 1rem' }}>
        <h2 style={{ color: 'var(--color-error)' }}>Error Loading Dashboard</h2>
        <Alert kind="error">{pageState.message}</Alert>
        <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
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
            <span style={{ fontSize: '24px' }} aria-hidden="true">
              ⚠️
            </span>
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
          <p style={{ margin: '0.4rem 0 0.8rem', color: 'var(--merchant-secondary)' }}>
            Your store deliveries, tracking, and fulfillment overview.
          </p>
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
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

      {/* Orders Table Section */}
      <section className={ordersStyles.orders} aria-labelledby="orders-title">
        <div className={ordersStyles.heading}>
          <div>
            <h2 id="orders-title">Merchant Store Deliveries</h2>
            <p style={{ margin: '0.25rem 0 0', color: 'var(--merchant-secondary)', fontSize: '0.875rem' }}>
              Monitor, filter, and view details for all deliveries created by your store.
            </p>
          </div>
          <MerchantLink className={shared.button} href={merchantPath('create-order', false)}>
            + Create Order
          </MerchantLink>
        </div>

        {/* Status Filter Buttons */}
        <div className={extra.filterBar} role="group" aria-label="Order status filters">
          {FILTER_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={`${extra.filterBtn} ${statusFilter === opt.value ? extra.filterBtnActive : ''}`}
              onClick={() => handleFilterChange(opt.value)}
            >
              {opt.label}
            </button>
          ))}
        </div>

        {/* Table / Empty State / Loading */}
        {loadingOrders ? (
          <div className={shared.state} style={{ textAlign: 'center', padding: '40px' }}>
            <p>Loading deliveries…</p>
          </div>
        ) : !deliveries || deliveries.content.length === 0 ? (
          <div className={ordersStyles.empty} style={{ textAlign: 'center', padding: '2rem 1rem' }}>
            {statusFilter === 'ALL' ? (
              <>
                <p style={{ fontWeight: 700, fontSize: '1.1rem', margin: '0 0 0.5rem', color: 'var(--merchant-text)' }}>
                  No orders yet
                </p>
                <p style={{ margin: '0 0 1rem', color: 'var(--merchant-secondary)' }}>
                  No orders have been placed for your store yet. Create your first delivery shipment to get started.
                </p>
                <MerchantLink className={shared.button} href={merchantPath('create-order', false)}>
                  + Create Order
                </MerchantLink>
              </>
            ) : (
              <>
                <p style={{ fontWeight: 700, fontSize: '1.1rem', margin: '0 0 0.5rem', color: 'var(--merchant-text)' }}>
                  No matching orders
                </p>
                <p style={{ margin: '0 0 1rem', color: 'var(--merchant-secondary)' }}>
                  No deliveries found with status "{STATUS_LABELS[statusFilter]}".
                </p>
                <button type="button" className={shared.button} onClick={() => handleFilterChange('ALL')}>
                  Clear Filter
                </button>
              </>
            )}
          </div>
        ) : (
          <>
            <div className={ordersStyles.tableRegion} role="region" aria-label="Store orders table" tabIndex={0}>
              <table className={ordersStyles.table} aria-labelledby="orders-title">
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
                      style={{ cursor: 'pointer' }}
                      onClick={() => setSelectedDeliveryId(delivery.id)}
                    >
                      <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>{delivery.trackingNumber}</td>
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
                          className={shared.button}
                          style={{ minHeight: '2rem', padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedDeliveryId(delivery.id);
                          }}
                        >
                          View Details
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
