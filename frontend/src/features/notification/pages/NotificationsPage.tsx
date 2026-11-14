import { useEffect, useState } from 'react';
import { navigate, routes } from '../../../app/router';
import {
  getNotifications,
  triggerTestNotification,
  type NotificationItem,
  type PageResponse,
} from '../api/notificationApi';
import styles from './NotificationsPage.module.css';

export function NotificationsPage() {
  const [data, setData] = useState<PageResponse<NotificationItem> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const loadPage = async (pageNumber: number) => {
    setLoading(true);
    try {
      const res = await getNotifications(pageNumber, 10);
      setData(res);
      setPage(res.page);
    } catch {
      // error handling
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Data fetch when the page number changes; state is set by the request (the first loading flag is already true)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPage(page);
  }, [page]);

  const handleCreateTest = async () => {
    try {
      await triggerTestNotification('SD-DEMO-777', 'CONFIRMED');
      void loadPage(page);
    } catch {
      // silent
    }
  };

  const formatDate = (isoString: string) => {
    try {
      const d = new Date(isoString);
      return d.toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const channelStyle = (channel: string) => {
    switch (channel) {
      case 'EMAIL':
        return { background: '#e2e8f0', color: '#334155' };
      case 'SMS':
        return { background: '#fef3c7', color: '#92400e' };
      default:
        return { background: '#dbeafe', color: '#1e40af' };
    }
  };

  return (
    <div className={styles.container}>
      <main className={styles.mainContent}>
        <div className={styles.header}>
          <div className={styles.titleArea}>
            <h1>Notifications & Alerts</h1>
            <p className={styles.subtitle}>
              Full history of all delivery updates sent via Email, SMS, or In-App.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              type="button"
              className={styles.backBtn}
              onClick={() => void handleCreateTest()}
            >
              + Send Test Notification
            </button>
            <button
              type="button"
              className={styles.backBtn}
              onClick={() => navigate(routes.customerDashboard)}
            >
              &larr; Back to Dashboard
            </button>
          </div>
        </div>

        <div className={styles.card}>
          <div className={styles.tableHeader}>
            <div>Channel</div>
            <div>Tracking #</div>
            <div>Subject & Message</div>
            <div>Sent Date</div>
          </div>

          {loading ? (
            <div className={styles.emptyState}>Loading notification logs...</div>
          ) : !data || data.content.length === 0 ? (
            <div className={styles.emptyState}>
              <strong>No notifications yet</strong>
              <p>You will see automated alerts whenever your deliveries update.</p>
            </div>
          ) : (
            data.content.map((item) => (
              <div key={item.id} className={styles.row}>
                <div>
                  <span
                    className={styles.channelPill}
                    style={channelStyle(item.channel)}
                  >
                    {item.channel}
                  </span>
                </div>
                <div className={styles.trackingNumber}>
                  {item.trackingNumber ? item.trackingNumber : '—'}
                </div>
                <div className={styles.contentCol}>
                  {item.subject && (
                    <div className={styles.subjectText}>{item.subject}</div>
                  )}
                  <div className={styles.bodyText}>{item.content}</div>
                </div>
                <div className={styles.dateCol}>{formatDate(item.createdAt)}</div>
              </div>
            ))
          )}

          {data && data.totalPages > 1 && (
            <div className={styles.pagination}>
              <div className={styles.pageInfo}>
                Page {data.page + 1} of {data.totalPages} ({data.totalElements} total)
              </div>
              <div className={styles.pageControls}>
                <button
                  type="button"
                  className={styles.pageBtn}
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className={styles.pageBtn}
                  disabled={data.page >= data.totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

