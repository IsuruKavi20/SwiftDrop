import { useState, type ReactNode } from 'react';
import { DispatcherIcon, type DispatcherIconName } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

/** Turns the most common technical failures into words an admin can act on; anything else is shown as it is. */
function friendly(message: string): { title: string; detail: string } {
  const text = message.toLowerCase();
  if (text.includes('failed to fetch') || text.includes('network') || text.includes('unavailable') || text.includes('503')) {
    return { title: "Can't reach SwiftDrop right now", detail: 'The server or your connection may be down. Check your internet, then try again.' };
  }
  if (text.includes('401') || text.includes('sign in') || text.includes('expired')) {
    return { title: 'Your sign-in has expired', detail: 'Sign in again to keep working.' };
  }
  if (text.includes('403') || text.includes('permission') || text.includes('not allowed')) {
    return { title: "You don't have access to this", detail: message };
  }
  return { title: "This couldn't be loaded", detail: message };
}

/**
 * A failed load: what happened in plain words, and a Retry button that loads the page's data again
 * (useDispatchData's reload). The original message stays visible underneath for support.
 */
export function LoadError({ message, onRetry }: Readonly<{ message: string; onRetry: () => void }>) {
  const { title, detail } = friendly(message);
  const [retrying, setRetrying] = useState(false);
  const retry = () => {
    setRetrying(true);
    onRetry();
    // The page re-renders with fresh data or a new error; re-enable the button shortly either way
    setTimeout(() => setRetrying(false), 1200);
  };
  return (
    <div className={styles.loadError} role="alert">
      <span className={styles.stateIcon} data-tone="red"><DispatcherIcon name="alert" size={20} /></span>
      <div>
        <strong>{title}</strong>
        <p>{detail}</p>
        {detail !== message && <small>{message}</small>}
      </div>
      <button className={styles.secondaryButton} type="button" onClick={retry} disabled={retrying}>
        <DispatcherIcon name="reassign" size={16} /> {retrying ? 'Retrying…' : 'Retry'}
      </button>
    </div>
  );
}

interface EmptyStateProps {
  icon: DispatcherIconName;
  title: string;
  children?: ReactNode;
  /** A next step, e.g. an "Add courier" or "Clear filters" button. */
  action?: ReactNode;
  /** Good news ("nothing failed"): a green icon instead of gold. */
  positive?: boolean;
  /** Smaller, for use inside a panel. */
  compact?: boolean;
}

/** An empty list that says why it's empty and what to do next. */
export function EmptyState({ icon, title, children, action, positive, compact }: Readonly<EmptyStateProps>) {
  return (
    <div className={styles.emptyState} data-compact={compact || undefined}>
      <span className={styles.stateIcon} data-tone={positive ? 'green' : 'amber'}><DispatcherIcon name={icon} size={compact ? 20 : 24} /></span>
      <strong>{title}</strong>
      {children && <p>{children}</p>}
      {action && <div className={styles.stateAction}>{action}</div>}
    </div>
  );
}
