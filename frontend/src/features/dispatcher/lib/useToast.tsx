import { useEffect, useState } from 'react';
import styles from '../components/Dispatcher.module.css';

/** A confirmation message that disappears after 5 seconds. Render `toast` once in the page. */
export function useToast() {
  const [message, setMessage] = useState<string>();
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(undefined), 5000);
    return () => clearTimeout(timer);
  }, [message]);
  const toast = (
    <div className={styles.toastArea} aria-live="polite">
      {message && <p className={styles.toast}>{message}</p>}
    </div>
  );
  return { showToast: setMessage, toast };
}
