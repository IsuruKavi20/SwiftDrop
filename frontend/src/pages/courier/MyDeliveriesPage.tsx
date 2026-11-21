import React, { useEffect, useState } from 'react';
import { TopHeader } from '../../components/layout/TopHeader';
import { DeliveryTable } from '../../components/courier/DeliveryTable';
import { DeliveryCardMobile } from '../../components/courier/DeliveryCardMobile';
import { SearchIcon, FilterIcon, RefreshIcon } from '../../components/common/Icons';
import { courierService } from '../../services/courier/courierService';
import type { DeliverySummary, StatusFilterOption } from '../../types/courier';

const STATUS_FILTERS: { key: StatusFilterOption; label: string }[] = [
  { key: 'ALL', label: 'All Shipments' },
  { key: 'PENDING', label: 'Pending' },
  { key: 'PICKED_UP', label: 'Picked Up' },
  { key: 'IN_TRANSIT', label: 'In Transit' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'FAILED', label: 'Failed' },
];

export const MyDeliveriesPage: React.FC = () => {
  const [deliveries, setDeliveries] = useState<DeliverySummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilterOption>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function fetchDeliveries() {
      try {
        setLoading(true);
        const data = await courierService.getAssignedDeliveries({
          status: statusFilter,
          search: searchQuery,
        });

        if (isMounted) {
          setDeliveries(data);
          setError(null);
        }
      } catch (err: unknown) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to retrieve deliveries.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    const timer = setTimeout(() => {
      fetchDeliveries();
    }, 150);

    return () => {
      isMounted = false;
      clearTimeout(timer);
    };
  }, [statusFilter, searchQuery]);

  return (
    <>
      <TopHeader
        title="My Deliveries"
        subtitle="Review, filter, and execute deliveries assigned to your route today."
      />

      <div className="content-card deliveries-master-card">
        {/* Controls Toolbar: Search & Status Filters */}
        <div className="deliveries-toolbar">
          {/* Modern Search Box */}
          <div className="search-input-wrapper">
            <SearchIcon size={17} color="var(--text-muted)" className="search-icon" />
            <input
              type="text"
              placeholder="Search by tracking number, recipient name, or street..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="search-input-field"
              aria-label="Search deliveries"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="search-clear-btn"
                title="Clear search"
                aria-label="Clear search query"
              >
                ✕
              </button>
            )}
          </div>

          <div className="toolbar-stats-counter">
            <span className="counter-number">{deliveries.length}</span>
            <span className="counter-label">Parcels Found</span>
          </div>
        </div>

        {/* Filter Segment Tabs */}
        <div className="filter-tabs-wrapper deliveries-filter-scroll" role="tablist" aria-label="Status filter tabs">
          <div className="filter-tabs-icon-label">
            <FilterIcon size={14} color="var(--text-muted)" />
            <span>Filter:</span>
          </div>
          {STATUS_FILTERS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={statusFilter === tab.key}
              className={`filter-tab ${statusFilter === tab.key ? 'active' : ''}`}
              onClick={() => setStatusFilter(tab.key)}
            >
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        {/* Table / List Container */}
        {loading ? (
          <div className="table-skeleton-list">
            <div className="skeleton-row shimmer" />
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
              <RefreshIcon size={14} />
              <span>Retry</span>
            </button>
          </div>
        ) : deliveries.length === 0 ? (
          <div className="deliveries-empty-container">
            <div className="empty-icon-halo">📦</div>
            <h3 className="empty-headline">No matching deliveries found</h3>
            <p className="empty-subtext">
              {searchQuery
                ? `No parcels match your search query "${searchQuery}".`
                : 'There are no parcels currently matching the selected status filter.'}
            </p>
            {(searchQuery || statusFilter !== 'ALL') && (
              <button
                type="button"
                className="btn-action secondary"
                onClick={() => {
                  setSearchQuery('');
                  setStatusFilter('ALL');
                }}
              >
                Reset All Filters
              </button>
            )}
          </div>
        ) : (
          <>
            <DeliveryTable deliveries={deliveries} />
            <div className="mobile-delivery-list">
              {deliveries.map((delivery) => (
                <DeliveryCardMobile key={delivery.id} delivery={delivery} />
              ))}
            </div>
          </>
        )}
      </div>
    </>
  );
};
