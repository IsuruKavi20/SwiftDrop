import React from 'react';
import { TopHeader } from '../../components/layout/TopHeader';
import { PackageIcon, AlertCircleIcon, BellIcon, SparklesIcon, TruckIcon } from '../../components/common/Icons';
import { env } from '../../config/env';

export const NotificationsPage: React.FC = () => {
  const isMockMode = env.isCourierMockMode;

  const mockNotifications = [
    {
      id: '1',
      title: 'New Delivery Assigned',
      message: 'Dispatcher assigned parcel SD-1048-7296 to your route in Kurunegala North zone.',
      time: '1 hour ago',
      type: 'INFO',
      icon: <PackageIcon size={18} color="#181B20" />,
    },
    {
      id: '2',
      title: 'Priority Dispatch Update',
      message: 'Parcel SD-1048-7291 has requested expedited morning delivery. Please prioritize on route.',
      time: '2 hours ago',
      type: 'ALERT',
      icon: <AlertCircleIcon size={18} color="#92400E" />,
    },
    {
      id: '3',
      title: 'Daily Shift Assigned',
      message: 'Good morning Sachin! 12 deliveries have been assigned to your shift for today.',
      time: 'Today, 07:15 AM',
      type: 'INFO',
      icon: <TruckIcon size={18} color="#181B20" />,
    },
  ];

  return (
    <>
      <TopHeader
        title="Notifications & Dispatch Alerts"
        subtitle="Operational broadcasts, route reassignments, and customer updates."
      />

      <div className="content-card notifications-master-card">
        <div className="card-header-row">
          <div>
            <h2 className="card-title">Recent System Broadcasts</h2>
            <span className="card-subtitle-small">Real-time alerts from SwiftDrop Dispatch</span>
          </div>
          {isMockMode ? (
            <span className="mock-preview-tag">
              <SparklesIcon size={13} color="#92400E" />
              Development Mock Preview
            </span>
          ) : (
            <span className="live-api-tag">
              <span className="live-status-dot" />
              Connected to SwiftDrop API
            </span>
          )}
        </div>

        {isMockMode ? (
          <div className="notifications-list">
            {mockNotifications.map((n) => (
              <div
                key={n.id}
                className={`notification-card ${n.type === 'ALERT' ? 'alert' : 'info'}`}
              >
                <div className={`notification-icon-wrap ${n.type === 'ALERT' ? 'alert' : 'info'}`}>
                  {n.icon}
                </div>

                <div className="notification-body">
                  <div className="notification-title-line">
                    <span className="notification-headline">{n.title}</span>
                    <span className="notification-sim-badge">SIMULATION</span>
                    <span className="notification-time">{n.time}</span>
                  </div>
                  <p className="notification-desc">{n.message}</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="notifications-empty-state">
            <div className="empty-bell-halo">
              <BellIcon size={28} color="var(--text-muted)" />
            </div>
            <h3 className="empty-headline">No Active Notifications</h3>
            <p className="empty-subtext">
              Real-time dispatch alerts, customer notes, and route reassignments will stream here once published by the dispatcher system.
            </p>
          </div>
        )}
      </div>
    </>
  );
};
