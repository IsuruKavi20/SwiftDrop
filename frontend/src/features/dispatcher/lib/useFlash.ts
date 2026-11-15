import { useCallback, useEffect, useState } from 'react';
import type { ToastOptions } from './useToast';

const FLASH_MS = 2600;

/**
 * Marks the table row that just changed (saved or undone) so it glows gold for a moment. Rows read `flashOf(key)`
 * into data-flash; the value alternates between "a" and "b" so flashing the same row twice replays the animation.
 */
export function useFlash() {
  const [flash, setFlash] = useState<{ key: string; run: number }>();

  useEffect(() => {
    if (!flash) return;
    const timer = setTimeout(() => setFlash(undefined), FLASH_MS);
    return () => clearTimeout(timer);
  }, [flash]);

  const mark = useCallback((key: string | undefined) => {
    if (key) setFlash((current) => ({ key, run: (current?.run ?? 0) + 1 }));
  }, []);

  /** The data-flash value for a row: "a"/"b" while it is the changed row, otherwise undefined. */
  const flashOf = (key: string | number | undefined) =>
    flash && key !== undefined && String(key) === flash.key ? (flash.run % 2 ? 'a' : 'b') : undefined;

  /** Wraps a toast's undo so the row glows again once the undo is done. */
  const withFlash = (options: ToastOptions | undefined, key: string | undefined): ToastOptions | undefined => {
    const undo = options?.undo;
    if (!undo || !key) return options;
    return { undo: async () => { await undo(); mark(key); } };
  };

  return { mark, flashOf, withFlash };
}
