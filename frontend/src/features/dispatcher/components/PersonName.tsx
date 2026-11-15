import type { ReactNode } from 'react';
import { initials, personName } from '../lib/names';
import styles from './Dispatcher.module.css';

interface PersonNameProps {
  name: string | null | undefined;
  email?: string | null;
  /** The person's id (kept for callers; every avatar uses the same gold). */
  id?: string;
  /** Extra lines under the name, e.g. a phone number or badges. */
  children?: ReactNode;
}

/** A person in a table: an initials avatar, their name (or one made from their email), and anything under it. */
export function PersonName({ name, email, children }: Readonly<PersonNameProps>) {
  const { display, missing } = personName(name, email);
  return (
    <span className={styles.person}>
      <span className={styles.personAvatar} data-tone="amber" aria-hidden="true">{initials(display)}</span>
      <span className={styles.personText}>
        <strong>{display}</strong>
        {missing && <small className={styles.cellSub}>No name set</small>}
        {children}
      </span>
    </span>
  );
}
