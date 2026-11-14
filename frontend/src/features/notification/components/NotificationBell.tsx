import { useEffect, useRef, useState } from 'react';
import {
  getNotificationCount,
  getNotifications,
  triggerTestNotification,
  type NotificationItem,
} from '../api/notificationApi';
import styles from './NotificationDropdown.module.css';

interface NotificationBellProps {
  onViewAll?: () => void;
}

export function NotificationBell({ onViewAll }: NotificationBellProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [count, setCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  // "Now" for the "5m ago" labels: read once, then updated by the poll timer (reading the clock while rendering
  // would give a different answer on every render)
  const [now, setNow] = useState(() => Date.now());

  // Load count on mount and periodically
  const refreshCount = async () => {
    try {
      const data = await getNotificationCount();
      setCount(data.count);
    } catch {
      // Ignore count fetch errors if server is restarting
    }
  };

  useEffect(() => {
    // refreshCount() only sets state after an await (a data fetch), which is what effects are for
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshCount();
    const interval = setInterval(() => {
      setNow(Date.now());
      void refreshCount();
    }, 20000); // 20s poll for updates
    return () => clearInterval(interval);
  }, []);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  // Load items when dropdown is opened
  const toggleDropdown = async () => {
    if (!isOpen) {
      setLoading(true);
      setIsOpen(true);
      try {
        const data = await getNotifications(0, 5);
        setNotifications(data.content);
      } catch {
        // error handling
      } finally {
        setLoading(false);
      }
    } else {
      setIsOpen(false);
    }
  };

  const handleTestAlert = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const newNotif = await triggerTestNotification('SD-LIVE-001', 'OUT_FOR_DELIVERY');
      setNotifications((prev) => [newNotif, ...prev.slice(0, 4)]);
      setCount((prev) => prev + 1);
    } catch {
      // silent
    }
  };

  const formatRelativeTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const diffMs = now - date.getTime();
      const diffMin = Math.floor(diffMs / 60000);
      if (diffMin < 1) return 'Just now';
      if (diffMin < 60) return `${diffMin}m ago`;
      const diffHours = Math.floor(diffMin / 60);
      if (diffHours < 24) return `${diffHours}h ago`;
      return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    } catch {
      return '';
    }
  };

  const channelClass = (channel: string) => {
    switch (channel) {
      case 'EMAIL':
        return styles.channelEmail;
      case 'SMS':
        return styles.channelSms;
      default:
        return styles.channelInApp;
    }
  };

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        type="button"
        className={`${styles.bellButton} ${isOpen ? styles.bellButtonActive : ''}`}
        aria-label="Notifications"
        aria-expanded={isOpen}
        onClick={() => void toggleDropdown()}
        title={count > 0 ? `${count} notifications` : 'Notifications'}
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>

        {count > 0 && <span className={styles.badge}>{count > 99 ? '99+' : count}</span>}
      </button>

      {isOpen && (
        <div className={styles.dropdown}>
          <div className={styles.header}>
            <h3>Notifications</h3>
            <button
              type="button"
              className={styles.testBtn}
              onClick={(e) => void handleTestAlert(e)}
              title="Generate a live test notification"
            >
              + Send Test Alert
            </button>
          </div>

          <div className={styles.list}>
            {loading ? (
              <div className={styles.empty}>Loading notifications...</div>
            ) : notifications.length === 0 ? (
              <div className={styles.empty}>No notifications yet.</div>
            ) : (
              notifications.map((n) => (
                <div key={n.id} className={styles.item}>
                  <div className={styles.itemTop}>
                    <span className={`${styles.channelBadge} ${channelClass(n.channel)}`}>{n.channel}</span>
                    <span className={styles.time}>{formatRelativeTime(n.createdAt)}</span>
                  </div>
                  {n.subject && <div className={styles.subject}>{n.subject}</div>}
                  <div className={styles.content}>{n.content}</div>
                </div>
              ))
            )}
          </div>

          {onViewAll && (
            <div className={styles.footer}>
              <button
                type="button"
                className={styles.footerButton}
                onClick={() => {
                  setIsOpen(false);
                  onViewAll();
                }}
              >
                View all notifications
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

