import type { SortState } from '../lib/useSort';
import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

interface SortHeaderProps<K extends string> {
  label: string;
  column: K;
  sort: SortState<K> | undefined;
  onSort: (column: K) => void;
  numeric?: boolean;
}

/** A column heading that sorts its table; screen readers hear the current order through aria-sort. */
export function SortHeader<K extends string>({ label, column, sort, onSort, numeric }: Readonly<SortHeaderProps<K>>) {
  const active = sort?.key === column;
  let ariaSort: 'ascending' | 'descending' | 'none' = 'none';
  if (active) ariaSort = sort.dir === 'asc' ? 'ascending' : 'descending';
  let icon: 'up' | 'down' | 'sort' = 'sort';
  if (active) icon = sort.dir === 'asc' ? 'up' : 'down';
  return (
    <th scope="col" aria-sort={ariaSort} className={numeric ? styles.numeric : undefined}>
      <button className={styles.sortButton} type="button" onClick={() => onSort(column)} data-active={active || undefined}>
        {label}
        <DispatcherIcon name={icon} size={13} />
      </button>
    </th>
  );
}
