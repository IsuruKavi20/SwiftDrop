import { useMemo } from 'react';
import { useUrlState } from './useUrlState';

type SortValue = string | number | null | undefined;
export type SortDir = 'asc' | 'desc';
export interface SortState<K extends string> { key: K; dir: SortDir }

const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

function compare(a: SortValue, b: SortValue): number {
  // Empty values always go last, whichever way the column is sorted
  if (a === null || a === undefined || a === '') return b === null || b === undefined || b === '' ? 0 : 1;
  if (b === null || b === undefined || b === '') return -1;
  return typeof a === 'number' && typeof b === 'number' ? a - b : collator.compare(String(a), String(b));
}

/**
 * Sorts a fully loaded list by a clicked column, kept in the address bar as ?sort=name or ?sort=-name (descending).
 * `columns` maps each sortable column to the value it sorts by. Without a sort, the list keeps the server's order.
 */
export function useSort<T, K extends string>(rows: readonly T[] | undefined, columns: Record<K, (row: T) => SortValue>) {
  const keys = Object.keys(columns) as K[];
  const [param, setParam] = useUrlState<string>('sort', '', ['', ...keys, ...keys.map((k) => `-${k}`)]);
  const sort: SortState<K> | undefined = param
    ? { key: param.replace(/^-/, '') as K, dir: param.startsWith('-') ? 'desc' : 'asc' }
    : undefined;

  const sorted = useMemo(() => {
    if (!rows || !sort) return rows;
    const value = columns[sort.key];
    const direction = sort.dir === 'asc' ? 1 : -1;
    // Empty values stay last in both directions
    return [...rows].sort((a, b) => {
      const va = value(a);
      const vb = value(b);
      const emptyA = va === null || va === undefined || va === '';
      const emptyB = vb === null || vb === undefined || vb === '';
      if (emptyA || emptyB) return compare(va, vb);
      return compare(va, vb) * direction;
    });
    // columns is a fresh object each render; the sort only depends on the key and direction
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, sort?.key, sort?.dir]);

  /** First click sorts ascending, the second descending, the third goes back to the server's order. */
  const toggle = (key: K) => {
    if (sort?.key !== key) setParam(key);
    else if (sort.dir === 'asc') setParam(`-${key}`);
    else setParam('');
  };

  return { rows: sorted, sort, toggle };
}
