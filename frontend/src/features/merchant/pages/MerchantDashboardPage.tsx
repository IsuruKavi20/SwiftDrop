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
import previewStyles from './MerchantDashboardPage.module.css';
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
    if (!token) {
      throw new MerchantApiError(401, 'Session expired');
    }
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

function RealMerchantDashboard() {
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
            onClick={() => navigate('/merchant/orders')}
          >
            Store Orders
          </button>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate('/merchant/create-order')}
          >
            Create Order
          </button>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate('/merchant/api-credentials')}
          >
            API Keys
          </button>
          <button
            type="button"
            className={styles.ghostButton}
            onClick={() => navigate('/merchant/webhooks')}
          >
            Webhooks
          </button>
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
              Customer Dashboard
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
            Sign out
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className={styles.main}>
        {/* Profile Completion Warning Banner */}
        {!dashboard.profileComplete && (
          <div className={styles.profileWarningBanner}>
            <div className={styles.bannerContent}>
              <span className={styles.bannerIcon} aria-hidden="true">
                ⚠️
              </span>
              <div>
                <span className={styles.bannerTitle}>Complete your merchant business profile</span>
                <p className={styles.bannerText}>
                  Your merchant business profile is missing required address or contact details.
                  Complete it to enable full dispatch services.
                </p>
              </div>
            </div>
            <button
              type="button"
              className={styles.bannerAction}
              onClick={() => navigate(routes.merchantProfile)}
            >
              Complete Profile →
            </button>
          </div>
        )}

        {/* Statistics Cards */}
        <section
          className={styles.statsSection}
          aria-label="Order summary statistics"
          role="region"
        >
          <div className={styles.statCard}>
            <span className={styles.statLabel}>Total Orders</span>
            <span className={styles.statValue}>{dashboard.totalOrders}</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statLabel}>Pending</span>
            <span className={styles.statValue}>{dashboard.pendingOrders}</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statLabel}>In Transit</span>
            <span className={styles.statValue}>{dashboard.inTransitOrders}</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statLabel}>Delivered</span>
            <span className={styles.statValue}>{dashboard.deliveredOrders}</span>
          </div>
        </section>

        {/* Orders Table Section */}
        <section className={styles.ordersSection}>
          <div className={styles.ordersSectionHeader}>
            <h2 className={styles.sectionTitle}>Merchant Store Deliveries</h2>

            {/* Status Filter Buttons */}
            <div className={styles.filterGroup}>
              {FILTER_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    statusFilter === opt.value
                      ? `${styles.filterButton} ${styles.filterButtonActive}`
                      : styles.filterButton
                  }
                  onClick={() => handleFilterChange(opt.value)}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Table / Empty State / Loading */}
          {loadingOrders ? (
            <div style={{ textAlign: 'center', padding: '40px', color: 'var(--color-text-muted)' }}>
              <p>Loading deliveries…</p>
            </div>
          ) : !deliveries || deliveries.content.length === 0 ? (
            <div className={styles.emptyState}>
              <p className={styles.emptyTitle}>No store deliveries found</p>
              <p className={styles.emptySubtitle}>
                {statusFilter === 'ALL'
                  ? 'Your store does not have any deliveries yet.'
                  : `No deliveries found with status "${STATUS_LABELS[statusFilter]}".`}
              </p>
            </div>
          ) : (
            <>
              <div className={styles.tableWrapper}>
                <table className={styles.ordersTable}>
                  <thead>
                    <tr>
                      <th>Tracking Number</th>
                      <th>Recipient</th>
                      <th>Destination</th>
                      <th>Type</th>
                      <th>Status</th>
                      <th>Date</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {deliveries.content.map((delivery) => (
                      <tr
                        key={delivery.id}
                        className={styles.tableRow}
                        onClick={() => setSelectedDeliveryId(delivery.id)}
                      >
                        <td className={styles.mono}>{delivery.trackingNumber}</td>
                        <td>{delivery.receiverName || '—'}</td>
                        <td>{delivery.destinationCity || '—'}</td>
                        <td>
                          <span className={styles.typeBadge}>{delivery.deliveryType}</span>
                        </td>
                        <td>
                          <span
                            className={`${styles.badge} ${
                              STATUS_TONE[delivery.status] || styles.toneNeutral
                            }`}
                          >
                            {STATUS_LABELS[delivery.status] || delivery.status}
                          </span>
                        </td>
                        <td>{formatDate(delivery.createdAt)}</td>
                        <td>
                          <button
                            type="button"
                            className={styles.ghostButton}
                            style={{ padding: '4px 10px', fontSize: '12px' }}
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
              <div className={styles.paginationBar}>
                <span className={styles.pageInfo}>
                  Showing page {deliveries.page + 1} of {Math.max(deliveries.totalPages, 1)} ({deliveries.totalElements} orders total)
                </span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    className={styles.ghostButton}
                    disabled={currentPage === 0}
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

export function MerchantDashboardPage({ preview }: { preview?: boolean } = {}) {
  if (preview) {
    return <PreviewMerchantDashboard />;
  }
  return <RealMerchantDashboard />;
}
