import React, { useEffect, useState } from 'react';
import { TopHeader } from '../../components/layout/TopHeader';
import { DeliveryTable } from '../../components/courier/DeliveryTable';
import { DeliveryCardMobile } from '../../components/courier/DeliveryCardMobile';
import { CheckCircleIcon, AlertCircleIcon, ClockIcon } from '../../components/common/Icons';
import { courierService } from '../../services/courier/courierService';
import type { DeliverySummary } from '../../types/courier';

export const HistoryPage: React.FC = () => {
  const [history, setHistory] = useState<DeliverySummary[]>([]);
  const [filter, setFilter] = useState<'ALL' | 'DELIVERED' | 'FAILED'>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    courierService.getDeliveryHistory()
      .then((data) => {
        if (isMounted) {
          setHistory(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to retrieve delivery history.');
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const deliveredCount = history.filter((d) => d.status === 'DELIVERED').length;
  const failedCount = history.filter((d) => d.status === 'FAILED').length;

  const filteredHistory = history.filter((d) => {
    if (filter === 'DELIVERED') return d.status === 'DELIVERED';
    if (filter === 'FAILED') return d.status === 'FAILED';
    return true;
  });

  return (
    <>
      <TopHeader
        title="Delivery History & Archive"
        subtitle="Historical audit log of executed deliveries and recorded handover proofs."
      />

      <div className="content-card history-master-card">
        {/* KPI Summary Banner */}
        <div className="history-summary-row">
          <div className="history-kpi-pill">
            <ClockIcon size={16} color="var(--text-muted)" />
            <span>Total Closed: <strong>{history.length}</strong></span>
          </div>
          <div className="history-kpi-pill green">
            <CheckCircleIcon size={16} color="#059669" />
            <span>Delivered: <strong>{deliveredCount}</strong></span>
          </div>
          <div className="history-kpi-pill red">
            <AlertCircleIcon size={16} color="#DC2626" />
            <span>Failed: <strong>{failedCount}</strong></span>
          </div>
        </div>

        {/* Filter Segment Tabs */}
        <div className="filter-tabs-wrapper" role="tablist" aria-label="History status filter">
          <button
            type="button"
            className={`filter-tab ${filter === 'ALL' ? 'active' : ''}`}
            onClick={() => setFilter('ALL')}
          >
            All Closed ({history.length})
          </button>
          <button
            type="button"
            className={`filter-tab ${filter === 'DELIVERED' ? 'active' : ''}`}
            onClick={() => setFilter('DELIVERED')}
          >
            Delivered ({deliveredCount})
          </button>
          <button
            type="button"
            className={`filter-tab ${filter === 'FAILED' ? 'active' : ''}`}
            onClick={() => setFilter('FAILED')}
          >
            Failed ({failedCount})
          </button>
        </div>

        {loading ? (
          <div className="table-skeleton-list">
            <div className="skeleton-row shimmer" />
            <div className="skeleton-row shimmer" />
            <div className="skeleton-row shimmer" />
          </div>
        ) : error ? (
          <div className="deliveries-error-banner">
            <p className="error-message">{error}</p>
            <button
              type="button"
              className="btn-action primary"
              onClick={() => window.location.reload()}
            >
              Retry
            </button>
          </div>
        ) : filteredHistory.length === 0 ? (
          <div className="deliveries-empty-container">
            <div className="empty-icon-halo">📁</div>
            <h3 className="empty-headline">No archived records found</h3>
            <p className="empty-subtext">Completed or failed deliveries from previous shifts will appear here.</p>
          </div>
        ) : (
          <>
            <DeliveryTable deliveries={filteredHistory} />
            <div className="mobile-delivery-list">
              {filteredHistory.map((delivery) => (
                <DeliveryCardMobile key={delivery.id} delivery={delivery} />
              ))}
            </div>
          </>
        )}
      </div>
    </>
  );
};
