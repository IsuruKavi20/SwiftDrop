import { useCallback, useEffect, useState } from 'react';
import logoIcon from '../../../assets/auth/logo-box.svg';
import { navigate, routes } from '../../../app/router';
import { logOut, refresh } from '../../auth/api/authApi';
import { Alert } from '../../auth/components/FormControls';
import { getAccessToken, readClaims, type AccessClaims } from '../../auth/lib/session';
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

const STATUS_TONE: Record<DeliveryStatus, string> = {
  PENDING: styles.toneNeutral,
  CONFIRMED: styles.toneInfo,
  PICKED_UP: styles.toneInfo,
  IN_TRANSIT: styles.toneInfo,
  OUT_FOR_DELIVERY: styles.toneInfo,
  DELIVERED: styles.toneSuccess,
  FAILED: styles.toneError,
  CANCELLED: styles.toneNeutral,
};

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

function getInitialPageState(): PageState {
  const token = getAccessToken();
  if (token) {
    try {
      const claims = readClaims(token);
      const permissions = claims.entitlements ?? [];
      if (!permissions.includes('merchant:api-access')) {
        return {
          kind: 'accessDenied',
          message:
            'You need merchant privileges to access this dashboard. Please sign in with an authorized merchant account.',
        };
      }
    } catch {
      // Ignored: malformed or expired token will be recovered via refresh flow
    }
  }
  return { kind: 'loading' };
}

async function fetchDashboardData(): Promise<{
  dashboard: MerchantDashboardData;
  deliveries: PageResponse<MerchantDeliverySummary>;
  claims: AccessClaims;
}> {
  let token = getAccessToken();
  if (!token) {
    const refreshed = await refresh();
    if (!refreshed) {
      throw new MerchantApiError(401, 'Session expired');
    }
    token = getAccessToken();
  }
  if (!token) {
    throw new MerchantApiError(401, 'Unauthenticated');
  }

  const claims = readClaims(token);
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

export function MerchantDashboardPage() {
  const [pageState, setPageState] = useState<PageState>(getInitialPageState);
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
          setPageState({ kind: 'ready', dashboard, claims });
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
              'Access forbidden. Your account does not have merchant API permissions.',
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
  }, []);

  useEffect(() => {
    const initialToken = getAccessToken();
    if (initialToken) {
      try {
        const claims = readClaims(initialToken);
        if (!(claims.entitlements ?? []).includes('merchant:api-access')) {
          return;
        }
      } catch {
        // proceed to loadDashboard
      }
    }
    return loadDashboard();
  }, [loadDashboard]);

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
      <div className={styles.page}>
        <div style={{ textAlign: 'center', padding: '64px 20px', color: 'var(--color-text-muted)' }}>
          <p>Loading merchant dashboard…</p>
        </div>
      </div>
    );
  }

  if (pageState.kind === 'accessDenied') {
    return (
      <div className={styles.page}>
        <main className={styles.main}>
          <div className={styles.ordersSection}>
            <h2 className={styles.sectionTitle} style={{ color: 'var(--color-error)' }}>
              Access Denied
            </h2>
            <Alert kind="error">{pageState.message}</Alert>
            <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={() => navigate(routes.account)}
              >
                Go to Account
              </button>
              <button
                type="button"
                className={styles.ghostButton}
                onClick={() => void onLogOut()}
              >
                Sign in with another account
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (pageState.kind === 'error') {
    return (
      <div className={styles.page}>
        <main className={styles.main}>
          <div className={styles.ordersSection}>
            <h2 className={styles.sectionTitle} style={{ color: 'var(--color-error)' }}>
              Error Loading Dashboard
            </h2>
            <Alert kind="error">{pageState.message}</Alert>
            <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={handleRetry}
              >
                Retry
              </button>
              <button
                type="button"
                className={styles.ghostButton}
                onClick={() => navigate(routes.account)}
              >
                Go to Account
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  const { dashboard, claims } = pageState;
  const permissions = claims.entitlements ?? [];
  const tradingName = dashboard.businessName || 'Merchant Portal';

  return (
    <div className={styles.page}>
      {/* Top Navbar */}
      <header className={styles.navbar}>
        <div className={styles.merchantIdentity}>
          <a
            href={routes.merchantDashboard}
            className={styles.navBrand}
            onClick={(e) => {
              e.preventDefault();
            }}
          >
            <span className={styles.logoMark}>
              <img src={logoIcon} width={20} height={20} alt="" />
            </span>
            SwiftDrop <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--color-text-muted)' }}>Merchant</span>
          </a>
          <span className={styles.tradingName}>• {tradingName}</span>
        </div>

        <div className={styles.navActions}>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate(routes.merchantProfile)}
          >
            Business Profile
          </button>

          {permissions.includes('parcel:read-own') && (
            <button
              type="button"
              className={styles.ghostButton}
              onClick={() => navigate(routes.customerDashboard)}
            >
              Customer View
            </button>
          )}

          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate(routes.account)}
          >
            Account
          </button>

          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => void onLogOut()}
          >
            Log out
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className={styles.main}>
        {/* Incomplete profile warning */}
        {!dashboard.profileComplete && (
          <div className={styles.bannerIncomplete} role="status">
            <div className={styles.bannerText}>
              <span className={styles.bannerTitle}>Complete your merchant business profile</span>
              <span className={styles.bannerSubtitle}>
                Add your business address and contact information to ensure seamless dispatch and receipt generation.
              </span>
            </div>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => navigate(routes.merchantProfile)}
            >
              Complete Profile →
            </button>
          </div>
        )}

        {/* Stat Cards */}
        <section className={styles.statsGrid} aria-label="Order summary statistics">
          <div className={styles.statCard}>
            <span className={styles.statLabel}>Total Orders</span>
            <span className={styles.statValue}>{dashboard.totalOrders}</span>
          </div>

          <div className={styles.statCard}>
            <span className={styles.statLabel}>Pending</span>
            <span className={styles.statValue} style={{ color: '#b7791f' }}>
              {dashboard.pendingOrders}
            </span>
          </div>

          <div className={styles.statCard}>
            <span className={styles.statLabel}>In Transit</span>
            <span className={styles.statValue} style={{ color: '#2b6cb0' }}>
              {dashboard.inTransitOrders}
            </span>
          </div>

          <div className={styles.statCard}>
            <span className={styles.statLabel}>Delivered</span>
            <span className={styles.statValue} style={{ color: 'var(--color-success)' }}>
              {dashboard.deliveredOrders}
            </span>
          </div>
        </section>

        {/* Filterable Orders Table */}
        <section className={styles.ordersSection}>
          <div className={styles.ordersHeader}>
            <h3 className={styles.sectionTitle}>Merchant Orders</h3>

            <div className={styles.filterGroup}>
              {FILTER_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={`${styles.filterPill} ${statusFilter === opt.value ? styles.filterPillActive : ''}`}
                  onClick={() => handleFilterChange(opt.value)}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {loadingOrders ? (
            <div className={styles.emptyState}>
              <p>Refreshing orders…</p>
            </div>
          ) : !deliveries || deliveries.content.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyTitle}>No orders found</div>
              <p>
                {statusFilter === 'ALL'
                  ? 'You have not booked any delivery orders yet.'
                  : `No orders currently matching the "${statusFilter}" status.`}
              </p>
            </div>
          ) : (
            <>
              <div className={styles.tableWrapper}>
                <table className={styles.ordersTable}>
                  <thead>
                    <tr>
                      <th>Tracking #</th>
                      <th>Receiver</th>
                      <th>Destination</th>
                      <th>Type</th>
                      <th>Status</th>
                      <th>Est. Delivery</th>
                      <th>Booked Date</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {deliveries.content.map((order) => (
                      <tr
                        key={order.id}
                        className={styles.tableRow}
                        onClick={() => setSelectedDeliveryId(order.id)}
                      >
                        <td className={styles.mono}>{order.trackingNumber}</td>
                        <td>{order.receiverName ?? '—'}</td>
                        <td>{order.destinationCity ?? '—'}</td>
                        <td>
                          <span className={styles.typeBadge}>{order.deliveryType}</span>
                        </td>
                        <td>
                          <span className={`${styles.badge} ${STATUS_TONE[order.status]}`}>
                            {STATUS_LABELS[order.status]}
                          </span>
                        </td>
                        <td>{formatDate(order.estimatedDeliveryDate)}</td>
                        <td>{formatDate(order.createdAt)}</td>
                        <td>
                          <button
                            type="button"
                            className={styles.ghostButton}
                            style={{ padding: '4px 10px', fontSize: '12px' }}
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedDeliveryId(order.id);
                            }}
                          >
                            View
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <div className={styles.paginationBar}>
                <span className={styles.pageInfo}>
                  Showing page {deliveries.page + 1} of {Math.max(deliveries.totalPages, 1)} ({deliveries.totalElements} orders total)
                </span>

                <div className={styles.paginationButtons}>
                  <button
                    type="button"
                    className={styles.ghostButton}
                    disabled={currentPage <= 0}
                    onClick={() => handlePageChange(currentPage - 1)}
                  >
                    ← Previous
                  </button>
                  <button
                    type="button"
                    className={styles.ghostButton}
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
      </main>

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
