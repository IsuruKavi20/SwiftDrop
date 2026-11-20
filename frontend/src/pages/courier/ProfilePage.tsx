import React, { useEffect, useState } from 'react';
import { TopHeader } from '../../components/layout/TopHeader';
import { courierService } from '../../services/courier/courierService';
import type { CourierProfile, DashboardStats } from '../../types/courier';
import { TruckIcon, PhoneIcon, ShieldCheckIcon, CheckCircleIcon, AlertCircleIcon, LocationPinIcon } from '../../components/common/Icons';

export const ProfilePage: React.FC = () => {
  const [profile, setProfile] = useState<CourierProfile | null>(null);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Phone number update state
  const [phoneNumber, setPhoneNumber] = useState<string>('');
  const [savingPhone, setSavingPhone] = useState<boolean>(false);
  const [phoneSaved, setPhoneSaved] = useState<boolean>(false);
  const [phoneError, setPhoneError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    Promise.all([
      courierService.getCourierProfile(),
      courierService.getDashboardStats(),
    ])
      .then(([p, s]) => {
        if (isMounted) {
          setProfile(p);
          setStats(s);
          setPhoneNumber(p.phoneNumber || '');
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to retrieve profile data.');
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

  const handleUpdatePhone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;

    if (!phoneNumber.trim()) {
      setPhoneError('Contact phone number cannot be blank.');
      return;
    }

    try {
      setSavingPhone(true);
      setPhoneError(null);
      const updated = await courierService.updateCourierProfile({
        phoneNumber: phoneNumber.trim(),
        vehicleType: profile.vehicleType,
      });
      setProfile(updated);
      setPhoneSaved(true);
      setTimeout(() => setPhoneSaved(false), 3000);
    } catch (err: unknown) {
      setPhoneError(err instanceof Error ? err.message : 'Failed to update contact number.');
    } finally {
      setSavingPhone(false);
    }
  };

  if (loading && !profile) {
    return (
      <div className="profile-skeleton-view">
        <div className="skeleton-row shimmer" style={{ height: '40px', marginBottom: '20px' }} />
        <div className="skeleton-stats-grid">
          <div className="skeleton-card shimmer" style={{ height: '320px' }} />
          <div className="skeleton-card shimmer" style={{ height: '320px' }} />
        </div>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="dashboard-error-card">
        <div className="dashboard-error-icon">
          <AlertCircleIcon size={32} color="#EF4444" />
        </div>
        <h2 className="dashboard-error-title">Profile Data Unavailable</h2>
        <p className="dashboard-error-text">
          {error || 'Unable to retrieve your operational profile from the server.'}
        </p>
        <button
          type="button"
          className="btn-action primary"
          onClick={() => window.location.reload()}
        >
          Retry Connection
        </button>
      </div>
    );
  }

  const initials = profile.fullName
    ? profile.fullName
        .split(' ')
        .filter(Boolean)
        .map((n) => n[0])
        .join('')
        .slice(0, 2)
        .toUpperCase()
    : 'CP';

  return (
    <>
      <TopHeader
        title="Courier Profile & Shift Settings"
        subtitle="Operational credentials, assigned fleet vehicle, and service territory."
      />

      {phoneSaved && (
        <div className="detail-toast-alert success">
          <div className="toast-left">
            <CheckCircleIcon size={18} color="#10B981" />
            <span>Contact phone number updated successfully!</span>
          </div>
          <button
            type="button"
            className="toast-close-btn"
            onClick={() => setPhoneSaved(false)}
          >
            ✕
          </button>
        </div>
      )}

      {phoneError && (
        <div className="detail-toast-alert error">
          <div className="toast-left">
            <AlertCircleIcon size={18} color="#EF4444" />
            <span>{phoneError}</span>
          </div>
          <button
            type="button"
            className="toast-close-btn"
            onClick={() => setPhoneError(null)}
          >
            ✕
          </button>
        </div>
      )}

      <div className="profile-grid">
        {/* Card 1: Official Identity & Credentials */}
        <div className="content-card profile-main-card">
          <div className="profile-badge-header">
            <div className="profile-large-avatar">
              {initials}
            </div>
            <div className="profile-identity-text">
              <div className="profile-name-row">
                <h2 className="profile-name-lg">{profile.fullName || 'Authorized Courier'}</h2>
                <span className="profile-verified-chip" title="Verified SwiftDrop Personnel">
                  <ShieldCheckIcon size={13} color="#059669" />
                  Verified
                </span>
              </div>
              <span className="profile-role-sub">Fleet Delivery Specialist</span>
            </div>
          </div>

          <div className="profile-details-list">
            <div className="profile-detail-row">
              <span className="profile-detail-label">Email Account</span>
              <span className="profile-detail-val">{profile.email || 'courier@getswiftdrop.me'}</span>
            </div>

            <div className="profile-detail-row">
              <span className="profile-detail-label">Assigned Vehicle</span>
              <div className="profile-val-badge">
                <TruckIcon size={14} color="var(--primary-active)" />
                <span>{profile.vehicleType || 'Motorcycle'}</span>
              </div>
            </div>

            <div className="profile-detail-row">
              <span className="profile-detail-label">Assigned Zone</span>
              <div className="profile-val-badge">
                <LocationPinIcon size={14} color="var(--primary-active)" />
                <span>{profile.assignedZone || 'Kurunegala North'}</span>
              </div>
            </div>

            <div className="profile-detail-row">
              <span className="profile-detail-label">Operational Status</span>
              <span className="profile-active-tag">
                <span className="live-status-dot" />
                Active Dispatch Eligible
              </span>
            </div>
          </div>

          {/* Contact Edit Form */}
          <div className="profile-edit-box">
            <h3 className="edit-box-title">Update Contact Phone</h3>
            <p className="edit-box-subtitle">
              Dispatchers and customers receive this contact number for arrival coordination.
            </p>
            <form onSubmit={handleUpdatePhone} className="profile-phone-form">
              <div className="phone-input-wrap">
                <PhoneIcon size={16} color="var(--text-muted)" className="phone-icon" />
                <input
                  type="text"
                  required
                  placeholder="+94 77 123 4567"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  className="profile-phone-input"
                />
              </div>
              <button
                type="submit"
                disabled={savingPhone || phoneNumber === profile.phoneNumber}
                className="btn-action primary"
              >
                {savingPhone ? 'Saving...' : 'Save Contact'}
              </button>
            </form>
          </div>

          {/* Operational Restrictions Notice */}
          <div className="profile-security-notice">
            <span className="notice-headline">🔒 Operational Territory Policy</span>
            <p className="notice-body">
              Your assigned delivery zone and active eligibility are controlled exclusively by SwiftDrop Dispatcher operations. To request route reassignments or leave status, contact your fleet administrator.
            </p>
          </div>
        </div>

        {/* Card 2: Shift Performance Metrics */}
        <div className="content-card profile-perf-card">
          <div className="card-header-row">
            <div>
              <h2 className="card-title">Shift Performance</h2>
              <span className="card-subtitle-small">Fulfillment telemetry for today</span>
            </div>
            <span className="perf-rate-pill">{stats?.completionPercentage ?? 0}% Complete</span>
          </div>

          <div className="perf-metric-stack">
            <div className="perf-metric-box gold">
              <div className="perf-box-header">
                <span>Total Workload</span>
                <span className="perf-box-val">{stats?.assignedToday ?? 0}</span>
              </div>
              <div className="perf-box-bar-track">
                <div className="perf-box-bar-fill gold" style={{ width: '100%' }} />
              </div>
            </div>

            <div className="perf-metric-box green">
              <div className="perf-box-header">
                <span>Successful Handover</span>
                <span className="perf-box-val">{stats?.delivered ?? 0}</span>
              </div>
              <div className="perf-box-bar-track">
                <div
                  className="perf-box-bar-fill green"
                  style={{
                    width: `${stats && stats.assignedToday > 0 ? Math.round((stats.delivered / stats.assignedToday) * 100) : 0}%`,
                  }}
                />
              </div>
            </div>

            <div className="perf-metric-box blue">
              <div className="perf-box-header">
                <span>Currently En Route</span>
                <span className="perf-box-val">{stats?.inProgress ?? 0}</span>
              </div>
              <div className="perf-box-bar-track">
                <div
                  className="perf-box-bar-fill blue"
                  style={{
                    width: `${stats && stats.assignedToday > 0 ? Math.round((stats.inProgress / stats.assignedToday) * 100) : 0}%`,
                  }}
                />
              </div>
            </div>

            <div className="perf-metric-box red">
              <div className="perf-box-header">
                <span>Attempt Failures</span>
                <span className="perf-box-val">{stats?.failed ?? 0}</span>
              </div>
              <div className="perf-box-bar-track">
                <div
                  className="perf-box-bar-fill red"
                  style={{
                    width: `${stats && stats.assignedToday > 0 ? Math.round((stats.failed / stats.assignedToday) * 100) : 0}%`,
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};
