import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TopHeader } from '../../components/layout/TopHeader';
import { StatCard } from '../../components/common/StatCard';
import { ProgressCard } from '../../components/courier/ProgressCard';
import { ActivityTimeline } from '../../components/courier/ActivityTimeline';
import { DeliveryTable } from '../../components/courier/DeliveryTable';
import { DeliveryCardMobile } from '../../components/courier/DeliveryCardMobile';
import {
  PackageIcon,
  TruckIcon,
  CheckCircleIcon,
  AlertCircleIcon,
  ParcelHeroGraphic,
  ArrowRightIcon,
  SparklesIcon,
} from '../../components/common/Icons';
import { courierService } from '../../services/courier/courierService';
import type {
  DashboardStats,
  DeliverySummary,
  ActivityUpdate,
  CourierProfile,
  StatusFilterOption,
} from '../../types/courier';

function getTimeBasedGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

export const DashboardPage: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [deliveries, setDeliveries] = useState<DeliverySummary[]>([]);
  const [updates, setUpdates] = useState<ActivityUpdate[]>([]);
  const [profile, setProfile] = useState<CourierProfile | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilterOption>('ALL');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadDashboardData() {
      try {
        setLoading(true);
        const [statsData, deliveriesData, updatesData, profileData] = await Promise.all([
          courierService.getDashboardStats(),
          courierService.getAssignedDeliveries({ status: statusFilter }),
          courierService.getRecentUpdates(),
          courierService.getCourierProfile(),
        ]);

        if (isMounted) {
          setStats(statsData);
          setDeliveries(deliveriesData.slice(0, 6)); // 6 preview items on dashboard
          setUpdates(updatesData);
          setProfile(profileData);
          setError(null);
        }
      } catch (err: unknown) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to load dashboard data.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadDashboardData();

    return () => {
      isMounted = false;
    };
  }, [statusFilter]);

  if (loading && !stats) {
    return (
      <div className="dashboard-skeleton-view" aria-label="Loading dashboard">
        <div className="skeleton-header-box shimmer" />
        <div className="skeleton-banner shimmer" />
        <div className="skeleton-stats-grid">
          <div className="skeleton-card shimmer" />
          <div className="skeleton-card shimmer" />
          <div className="skeleton-card shimmer" />
          <div className="skeleton-card shimmer" />
        </div>
        <div className="skeleton-content-grid">
          <div className="skeleton-card-large shimmer" />
          <div className="skeleton-card-side shimmer" />
        </div>
      </div>
    );
  }

  if (error || !stats) {
    return (
      <div className="dashboard-error-card">
        <div className="dashboard-error-icon">
          <AlertCircleIcon size={32} color="#EF4444" />
        </div>
        <h2 className="dashboard-error-title">Unable to load Courier Dashboard</h2>
        <p className="dashboard-error-text">
          {error || 'Unable to retrieve real-time courier telemetry. Please check network connection.'}
        </p>
        <button
          type="button"
          className="btn-action primary"
          onClick={() => window.location.reload()}
        >
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  const firstName = profile?.fullName?.split(' ')[0] || 'Courier';
  const greeting = getTimeBasedGreeting();
  const totalParcels = stats.assignedToday || (stats.inProgress + stats.delivered + stats.failed);
  const deliveredPct = stats.completionPercentage;
  const inProgressPct = totalParcels > 0 ? Math.round((stats.inProgress / totalParcels) * 100) : 0;
  const failedPct = totalParcels > 0 ? Math.round((stats.failed / totalParcels) * 100) : 0;

  return (
    <>
      {/* Top Header */}
      <TopHeader
        title={`${greeting}, ${firstName}!`}
        subtitle="Live delivery operations, assigned parcels, and fulfillment tracking."
        courierName={profile?.fullName}
        courierZone={profile?.assignedZone}
      />

      {/* Hero Welcome Banner */}
      <section className="welcome-banner" aria-label="Welcome announcement">
        <div className="welcome-content">
          <div className="welcome-badge">
            <SparklesIcon size={13} color="#D9961A" />
            <span>Shift in Progress • {profile?.assignedZone || 'Assigned Zone'}</span>
          </div>
          <h2 className="welcome-title">Ready for your scheduled route?</h2>
          <p className="welcome-subtitle">
            You have <strong>{stats.assignedToday}</strong> parcels assigned today. <strong>{stats.delivered}</strong> completed
            ({stats.completionPercentage}% fulfillment) and <strong>{stats.inProgress}</strong> in transit.
          </p>
          <div className="welcome-actions-row">
            <Link to="/courier/deliveries" className="btn-welcome-primary">
              <span>View Route Deliveries</span>
              <ArrowRightIcon size={15} />
            </Link>
            <div className="welcome-stat-pill">
              <span className="welcome-stat-dot" />
              <span>{stats.inProgress} Active En Route</span>
            </div>
          </div>
        </div>
        <div className="banner-illustration" aria-hidden="true">
          <ParcelHeroGraphic />
        </div>
      </section>

      {/* 4 KPI Statistics Cards */}
      <section className="stats-grid" aria-label="Key delivery statistics">
        <StatCard
          label="Assigned Today"
          value={stats.assignedToday}
          variant="gold"
          icon={<PackageIcon size={22} color="#D9961A" />}
          subtitle="Total shift volume"
          progressPercent={100}
        />
        <StatCard
          label="In Progress"
          value={stats.inProgress}
          variant="blue"
          icon={<TruckIcon size={22} color="#2563EB" />}
          subtitle="Active on route"
          progressPercent={inProgressPct}
        />
        <StatCard
          label="Delivered"
          value={stats.delivered}
          variant="green"
          icon={<CheckCircleIcon size={22} color="#059669" />}
          subtitle="Confirmed drop-offs"
          progressPercent={deliveredPct}
        />
        <StatCard
          label="Failed / Delayed"
          value={stats.failed}
          variant="red"
          icon={<AlertCircleIcon size={22} color="#DC2626" />}
          subtitle="Requires attention"
          progressPercent={failedPct}
        />
      </section>

      {/* Dashboard Content Split Grid */}
      <div className="dashboard-content-grid">
        {/* Left Column: My Deliveries Preview */}
        <section className="dashboard-left-col">
          <div className="content-card">
            <div className="card-header-row">
              <div>
                <h2 className="card-title">Assigned Parcels</h2>
                <span className="card-subtitle-small">Showing priority route parcels for today</span>
              </div>
              <Link to="/courier/deliveries" className="card-link-subtle">
                <span>View All Deliveries</span>
                <ArrowRightIcon size={14} />
              </Link>
            </div>

            {/* Horizontal Filter Tabs */}
            <div className="filter-tabs-wrapper" role="tablist" aria-label="Status filters">
              {(['ALL', 'PENDING', 'IN_TRANSIT', 'DELIVERED'] as StatusFilterOption[]).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  role="tab"
                  aria-selected={statusFilter === tab}
                  className={`filter-tab ${statusFilter === tab ? 'active' : ''}`}
                  onClick={() => setStatusFilter(tab)}
                >
                  {tab === 'ALL' ? 'All Parcels' : tab.replace('_', ' ')}
                </button>
              ))}
            </div>

            {/* Desktop Table View */}
            <DeliveryTable deliveries={deliveries} />

            {/* Mobile Stacked Cards View */}
            <div className="mobile-delivery-list">
              {deliveries.map((delivery) => (
                <DeliveryCardMobile key={delivery.id} delivery={delivery} />
              ))}
            </div>
          </div>
        </section>

        {/* Right Column: Progress Card & Activity Timeline */}
        <aside className="dashboard-right-col">
          <ProgressCard stats={stats} />
          <ActivityTimeline updates={updates} />
        </aside>
      </div>
    </>
  );
};
