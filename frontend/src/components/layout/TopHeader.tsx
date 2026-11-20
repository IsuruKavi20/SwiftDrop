import React from 'react';
import { useNavigate } from 'react-router-dom';
import { BellIcon, ShieldCheckIcon } from '../common/Icons';

interface TopHeaderProps {
  title?: string;
  subtitle?: string;
  courierName?: string;
  courierZone?: string;
}

export const TopHeader: React.FC<TopHeaderProps> = ({
  title = 'Courier Dashboard',
  subtitle = 'Manage assigned parcels and update delivery progress.',
  courierName = 'Sachin Maleesha',
  courierZone = 'Kurunegala North',
}) => {
  const navigate = useNavigate();

  const initials = courierName
    .split(' ')
    .filter(Boolean)
    .map((n) => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase() || 'CO';

  return (
    <header className="top-header">
      <div className="header-left">
        <div className="header-title-row">
          <h1>{title}</h1>
          <div className="header-status-pill" title="Live Courier Dispatch Telemetry Connected">
            <span className="live-status-dot" />
            <span className="live-status-text">ON SHIFT</span>
          </div>
        </div>
        <p>{subtitle}</p>
      </div>

      <div className="header-actions">
        {/* Notification Bell */}
        <button
          type="button"
          className="icon-btn header-notification-btn"
          aria-label="View Notifications"
          title="Notifications & Dispatch Broadcasts"
          onClick={() => navigate('/courier/notifications')}
        >
          <BellIcon size={19} color="var(--text-primary)" />
          <span className="notification-badge-dot" />
        </button>

        {/* Profile Chip */}
        <div
          className="profile-chip"
          role="button"
          tabIndex={0}
          title="View Courier Profile & Shift Settings"
          onClick={() => navigate('/courier/profile')}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && navigate('/courier/profile')}
        >
          <div className="avatar">
            {initials}
          </div>
          <div className="profile-info">
            <span className="profile-name">
              {courierName}
              <ShieldCheckIcon size={13} color="#10B981" />
            </span>
            <span className="profile-role">{courierZone}</span>
          </div>
        </div>
      </div>
    </header>
  );
};
