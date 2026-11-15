import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

interface SearchBoxProps {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder: string;
}

export function SearchBox({ value, onChange, label, placeholder }: SearchBoxProps) {
  return (
    <label className={styles.search}>
      <span className={styles.visuallyHidden}>{label}</span>
      <DispatcherIcon name="search" size={18} />
      <input type="search" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}
