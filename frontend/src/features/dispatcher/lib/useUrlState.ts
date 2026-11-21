import { useCallback, useState } from 'react';

function readParam(key: string): string | null {
  return new URLSearchParams(window.location.search).get(key);
}

/** Writes one query parameter in place (no new history entry per keystroke); the default value is left out. */
function writeParam(key: string, value: string, fallback: string) {
  const url = new URL(window.location.href);
  if (value === fallback || value === '') url.searchParams.delete(key);
  else url.searchParams.set(key, value);
  const next = `${url.pathname}${url.search}${url.hash}`;
  if (next !== `${window.location.pathname}${window.location.search}${window.location.hash}`) {
    window.history.replaceState(window.history.state, '', next);
  }
}

/**
 * A filter kept in the address bar (?key=value), so a refresh, the back button or a shared link opens the list as it
 * was. `allowed` limits it to known values (anything else in the URL falls back to the default).
 *
 * const [status, setStatus] = useUrlState('status', '', ['', 'PENDING', 'FAILED']);
 */
export function useUrlState<T extends string>(key: string, fallback: T, allowed?: readonly T[]): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => {
    const raw = readParam(key);
    if (raw === null) return fallback;
    return !allowed || (allowed as readonly string[]).includes(raw) ? (raw as T) : fallback;
  });
  const update = useCallback((next: T) => {
    setValue(next);
    writeParam(key, next, fallback);
  }, [key, fallback]);
  return [value, update];
}

/** A whole number in the address bar (e.g. ?page=3, shown 1-based while stored 0-based by the caller). */
export function useUrlNumber(key: string, fallback: number, { min = 0, offset = 0 } = {}): [number, (value: number) => void] {
  const [value, setValue] = useState<number>(() => {
    const parsed = Number.parseInt(readParam(key) ?? '', 10);
    return Number.isFinite(parsed) && parsed - offset >= min ? parsed - offset : fallback;
  });
  const update = useCallback((next: number) => {
    setValue(next);
    writeParam(key, String(next + offset), String(fallback + offset));
  }, [key, fallback, offset]);
  return [value, update];
}
