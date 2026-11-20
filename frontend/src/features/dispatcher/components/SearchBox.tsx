import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

interface SearchBoxProps {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder: string;
}

/** The page's search field; the "/" shortcut (DispatcherLayout) jumps here, and Escape clears it. */
export function SearchBox({ value, onChange, label, placeholder }: Readonly<SearchBoxProps>) {
  return (
    <label className={styles.search}>
      <span className={styles.visuallyHidden}>{label}</span>
      <DispatcherIcon name="search" size={18} />
      <input type="search" value={value} placeholder={placeholder} data-page-search
        aria-keyshortcuts="/"
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Escape' && value) { e.preventDefault(); onChange(''); } }} />
      {!value && <kbd className={styles.kbdHint} aria-hidden="true">/</kbd>}
    </label>
  );
}
