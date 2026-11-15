import { useEffect, useState } from 'react';
import { DISPATCH_CHANGED_EVENT } from '../api/dispatcherApi';
import { useDispatchData } from './useDispatchData';

type Counts = Partial<Record<string, number>>;

/** How much each count went up between two loads (only the ones that grew). */
function growth(before: Counts, after: Counts): Counts {
  const grown: Counts = {};
  for (const [key, value] of Object.entries(after)) {
    const old = before[key];
    if (value !== undefined && old !== undefined && value > old) grown[key] = value - old;
  }
  return grown;
}

/**
 * Sidebar counts that stay current: reloaded every `intervalMs` while the tab is visible, and as soon as the admin
 * comes back to the tab (hidden tabs don't poll). Returns the counts and which of them just went up, so the sidebar
 * can draw attention to new work. When something grew, open lists reload too (the dispatch "changed" event).
 */
export function useLiveCounts<T extends Counts>(load: () => Promise<T>, intervalMs = 60_000) {
  const [tick, setTick] = useState(0);
  const { data } = useDispatchData(load, [tick]);

  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible') setTick((t) => t + 1); };
    const timer = setInterval(refresh, intervalMs);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [intervalMs]);

  // Compare each new answer with the last one (state updated during render: React's "derive from a change" pattern)
  const [seen, setSeen] = useState<{ data?: T; grown: Counts }>({ grown: {} });
  if (data && data !== seen.data) {
    setSeen({ data, grown: seen.data ? growth(seen.data, data) : {} });
  }

  const grewSomething = Object.keys(seen.grown).length > 0;
  useEffect(() => {
    if (grewSomething) window.dispatchEvent(new Event(DISPATCH_CHANGED_EVENT));
  }, [seen, grewSomething]);

  return { counts: data, grown: seen.grown as Partial<Record<keyof T, number>> };
}
