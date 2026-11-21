import React from 'react';
import { Link } from 'react-router-dom';
import type { ActivityUpdate } from '../../types/courier';
import { CheckCircleIcon, TruckIcon, PackageIcon, ChevronRightIcon } from '../common/Icons';

interface ActivityTimelineProps {
  updates: ActivityUpdate[];
}

export const ActivityTimeline: React.FC<ActivityTimelineProps> = ({ updates }) => {
  const getBadgeStyle = (status: string) => {
    switch (status) {
      case 'DELIVERED':
        return {
          className: 'timeline-green',
          icon: <CheckCircleIcon size={15} color="#FFFFFF" />,
        };
      case 'IN_TRANSIT':
      case 'OUT_FOR_DELIVERY':
        return {
          className: 'timeline-blue',
          icon: <TruckIcon size={15} color="#FFFFFF" />,
        };
      default:
        return {
          className: 'timeline-gold',
          icon: <PackageIcon size={15} color="#181B20" />,
        };
    }
  };

  return (
    <div className="content-card activity-timeline-widget">
      <div className="card-header-row">
        <div>
          <h2 className="card-title">Live Dispatch Feed</h2>
          <span className="card-subtitle-small">Recent parcel status transitions</span>
        </div>
        <Link to="/courier/history" className="card-link-subtle">
          <span>All History</span>
          <ChevronRightIcon size={14} />
        </Link>
      </div>

      <div className="timeline-list">
        {updates.length === 0 ? (
          <div className="timeline-empty-feed">
            <p>No activity updates recorded yet for this shift.</p>
          </div>
        ) : (
          updates.map((item) => {
            const badge = getBadgeStyle(item.status);
            return (
              <Link
                key={item.id}
                to={`/courier/deliveries/${item.trackingNumber}`}
                className="timeline-item timeline-item-clickable"
              >
                <div className={`timeline-icon-badge ${badge.className}`}>
                  {badge.icon}
                </div>
                <div className="timeline-content">
                  <div className="timeline-header-line">
                    <span className="timeline-tracking-code">{item.trackingNumber}</span>
                    <span className="timeline-status-tag">{item.status.replace(/_/g, ' ')}</span>
                  </div>
                  <div className="timeline-details-line">
                    <span className="timeline-recipient-name">{item.recipientName}</span>
                    <span className="timeline-time-text">• {item.timestamp}</span>
                  </div>
                </div>
              </Link>
            );
          })
        )}
      </div>
    </div>
  );
};
