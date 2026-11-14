import { useEffect, useState } from 'react';
import {
  getNotifications,
  triggerTestNotification,
  type NotificationItem,
  type PageResponse,
} from '../api/notificationApi';
import { Icon, type IconName } from '../../../shared/ui/Icon';
import styles from './NotificationsPage.module.css';

const channelIcon = (channel: string): IconName => (channel === 'EMAIL' ? 'mail' : channel === 'SMS' ? 'message' : 'bell');
const defaultTitle = (channel: string) => (channel === 'SMS' ? 'Text message' : channel === 'EMAIL' ? 'Email' : 'Update');

/** Emails are stored as HTML: show only their words here (parsed as text, never inserted as HTML). */
function asText(content: string): string {
  if (!content.includes('<')) return content;
  const text = new DOMParser().parseFromString(content, 'text/html').body.textContent ?? '';
  return text.replace(/\s+/g, ' ').trim();
}

export function NotificationsPage() {
  // "Show older": page 0 first, then each next page of 10 is added under the list (same backend paging)
  const [data, setData] = useState<PageResponse<NotificationItem> | null>(null);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');

  const loadPage = async (pageNumber: number) => {
    if (pageNumber === 0) setLoading(true); else setLoadingMore(true);
    setError('');
    try {
      const res = await getNotifications(pageNumber, 10);
      setData(res);
      // keep earlier pages; skip any item already shown (a new alert can shift the pages)
      setItems((old) => pageNumber === 0 ? res.content : [...old, ...res.content.filter((n) => !old.some((o) => o.id === n.id))]);
    } catch {
      setError('Could not load notifications. Please try again.');
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    // Data fetch when the page number changes; state is set by the request (the first loading flag is already true)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPage(page);
  }, [page]);

  const hasMore = data !== null && data.page < data.totalPages - 1;

  const handleCreateTest = async () => {
    try {
      await triggerTestNotification('SD-DEMO-777', 'CONFIRMED');
      if (page === 0) void loadPage(0); else setPage(0);
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


  return (
    <div className={styles.container}>
      <main className={styles.mainContent}>
        <div className={styles.header}>
          <div className={styles.titleArea}>
            <h2>Notifications & alerts</h2>
            <p className={styles.subtitle}>Every delivery update we sent you by email, SMS or in the app.</p>
          </div>
          {/* Developer helper from the notification module: creates one sample alert */}
          <button type="button" className={styles.testBtn} onClick={() => void handleCreateTest()}>
            Send a test notification
          </button>
        </div>

        <div className={styles.card}>
          {loading ? (
            <div className={styles.skeleton} role="status"><span className={styles.srOnly}>Loading…</span><i /><i /><i /></div>
          ) : error && items.length === 0 ? (
            <div className={styles.emptyState} role="alert"><strong>Something went wrong</strong><p>{error}</p></div>
          ) : items.length === 0 ? (
            <div className={styles.emptyState}>
              <span className={styles.emptyIcon}><Icon name="bell" size={26} /></span>
              <strong>No notifications yet</strong>
              <p>You will see an alert here whenever one of your deliveries changes.</p>
            </div>
          ) : (
            <ul className={styles.feed}>
              {items.map((item) => (
                <li key={item.id} className={styles.item}>
                  <span className={`${styles.channelIcon} ${styles[`ch${item.channel}`] ?? ''}`}>
                    <Icon name={channelIcon(item.channel)} size={18} />
                  </span>
                  <div className={styles.itemBody}>
                    <div className={styles.itemTop}>
                      <strong className={styles.subjectText}>{item.subject || defaultTitle(item.channel)}</strong>
                      <time className={styles.dateCol} dateTime={item.createdAt}>{formatDate(item.createdAt)}</time>
                    </div>
                    <p className={styles.bodyText}>{asText(item.content)}</p>
                    <div className={styles.itemMeta}>
                      <span className={styles.channelPill}>{item.channel}</span>
                      {item.trackingNumber && <span className={styles.trackingNumber}>{item.trackingNumber}</span>}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {items.length > 0 && (
            <div className={styles.pagination}>
              <span className={styles.pageInfo} aria-live="polite">
                Showing {items.length} of {data?.totalElements ?? items.length}
              </span>
              {hasMore ? (
                <button type="button" className={styles.pageBtn} disabled={loadingMore} onClick={() => setPage((p) => p + 1)}>
                  {loadingMore ? 'Loading…' : 'Show older'}
                </button>
              ) : (
                <span className={styles.pageInfo}>That's everything</span>
              )}
            </div>
          )}
          {error && items.length > 0 && <p className={styles.inlineError} role="alert">{error}</p>}
        </div>
      </main>
    </div>
  );
}

