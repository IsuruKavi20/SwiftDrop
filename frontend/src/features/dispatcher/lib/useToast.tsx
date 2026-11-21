import { useCallback, useEffect, useRef, useState } from 'react';
import styles from '../components/Dispatcher.module.css';

export interface ToastOptions {
  /** Reverses the change just made (e.g. saves the previous values again). Shows an Undo button. */
  undo?: () => Promise<void>;
}

interface ToastState {
  id: number;
  message: string;
  tone: 'done' | 'error';
  undo?: () => Promise<void>;
  busy?: boolean;
}

const SHOW_MS = 5000;
const SHOW_WITH_UNDO_MS = 8000;   // a little longer, so there's time to notice and undo

/**
 * A short message after a change, at the bottom of the screen. With `undo`, it offers an Undo button (and Ctrl/Cmd+Z
 * while it shows) that reverses the change. Render `toast` once in the page.
 */
export function useToast() {
  const [current, setCurrent] = useState<ToastState>();
  const nextId = useRef(0);

  const showToast = useCallback((message: string, options?: ToastOptions) => {
    nextId.current += 1;
    setCurrent({ id: nextId.current, message, tone: 'done', undo: options?.undo });
  }, []);

  // Hides the message after a while; a new message (new id) starts the clock again
  useEffect(() => {
    if (!current || current.busy) return;
    const timer = setTimeout(() => setCurrent(undefined), current.undo ? SHOW_WITH_UNDO_MS : SHOW_MS);
    return () => clearTimeout(timer);
  }, [current]);

  const undo = useCallback(async () => {
    const toast = current;
    if (!toast?.undo || toast.busy) return;
    setCurrent({ ...toast, busy: true });
    try {
      await toast.undo();
      nextId.current += 1;
      setCurrent({ id: nextId.current, message: 'Undone. The previous values are back.', tone: 'done' });
    } catch (failure) {
      nextId.current += 1;
      setCurrent({ id: nextId.current, message: failure instanceof Error ? `Couldn't undo: ${failure.message}` : "Couldn't undo. Please change it back by hand.", tone: 'error' });
    }
  }, [current]);

  // Ctrl/Cmd+Z undoes while the message shows, unless the admin is typing in a field
  useEffect(() => {
    if (!current?.undo) return;
    const onKey = (event: KeyboardEvent) => {
      const typing = event.target instanceof HTMLElement && event.target.closest('input, textarea, [contenteditable="true"]');
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z' && !event.shiftKey && !typing) {
        event.preventDefault();
        void undo();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [current, undo]);

  const toast = (
    <div className={styles.toastArea} aria-live="polite">
      {current && (
        <div key={current.id} className={styles.toast} data-tone={current.tone}>
          <span>{current.message}</span>
          {current.undo && (
            <button className={styles.toastAction} type="button" onClick={() => void undo()} disabled={current.busy}
              title="Undo (Ctrl+Z)">
              {current.busy ? 'Undoing…' : 'Undo'}
            </button>
          )}
        </div>
      )}
    </div>
  );
  return { showToast, toast };
}
