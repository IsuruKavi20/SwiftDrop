import { useCallback, useEffect, useRef, useState } from 'react';
import { DISPATCH_CHANGED_EVENT } from '../api/dispatcherApi';

/** The value after it stops changing for `ms` (for search boxes). */
export function useDebounced<T>(value: T, ms = 250): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms]);
  return settled;
}

export interface DispatchData<T> {
  data: T | undefined;
  error: string | undefined;
  loading: boolean;
  reload: () => void;
}

/**
 * Loads data for a dispatcher screen and reloads it when `deps` change or an assignment changes anywhere.
 * Answers from an older request are ignored, so fast typing in a search box never shows stale results.
 */
export function useDispatchData<T>(load: () => Promise<T>, deps: readonly unknown[]): DispatchData<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();
  const [version, setVersion] = useState(0);
  // Loading until the answer for the current request arrives (deps are plain values: ids, text, page numbers)
  const requestKey = JSON.stringify([version, ...deps]);
  const [settledKey, setSettledKey] = useState<string>();
  const loading = settledKey !== requestKey;
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    window.addEventListener(DISPATCH_CHANGED_EVENT, reload);
    return () => window.removeEventListener(DISPATCH_CHANGED_EVENT, reload);
  }, [reload]);

  useEffect(() => {
    let current = true;
    loadRef.current()
      .then((result) => {
        if (!current) return;
        setData(result);
        setError(undefined);
      })
      .catch((reason: unknown) => {
        if (current) setError(reason instanceof Error ? reason.message : 'Something went wrong. Please try again.');
      })
      .finally(() => {
        if (current) setSettledKey(requestKey);
      });
    return () => {
      current = false;
    };
  }, [requestKey]);

  return { data, error, loading, reload };
}
