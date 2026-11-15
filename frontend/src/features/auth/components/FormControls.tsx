import { useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
import arrowIcon from '../../../assets/auth/icon-arrow.svg';
import { Icon, type IconName } from '../../../shared/ui/Icon';
import styles from './FormControls.module.css';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  icon: string;
}

/** Figma "Text Field": label above, an input box with an icon bubble. Password fields get a show/hide eye. */
export function TextField({ label, icon, id, type, ...input }: TextFieldProps) {
  const [visible, setVisible] = useState(false);
  const isPassword = type === 'password';
  return (
    <label className={styles.field} htmlFor={id}>
      <span className={styles.label}>{label}</span>
      <span className={styles.inputBox}>
        <span className={styles.iconBubble}>
          <img src={icon} width={16} height={16} alt="" />
        </span>
        <input id={id} className={styles.input} type={isPassword && visible ? 'text' : type} {...input} />
        {isPassword && (
          <button
            type="button"
            className={styles.eye}
            onClick={() => setVisible((shown) => !shown)}
            aria-label={visible ? 'Hide password' : 'Show password'}
            aria-pressed={visible}
            aria-controls={id}
          >
            <Icon name={visible ? 'eyeOff' : 'eye'} size={18} />
          </button>
        )}
      </span>
    </label>
  );
}

/** Figma "Button/Primary": the main action, with an arrow. */
export function PrimaryButton({ children, ...button }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={`${styles.button} ${styles.primary}`} {...button}>
      {children}
      <img src={arrowIcon} width={20} height={20} alt="" />
    </button>
  );
}

/** Figma "Button/Secondary": Continue with Google. */
export function GoogleButton(button: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`${styles.button} ${styles.secondary}`} {...button}>
      {/* The official multi-colour Google "G" (Google sign-in branding guidelines) */}
      <svg className={styles.googleMark} width="20" height="20" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
      </svg>
      Continue with Google
    </button>
  );
}

export interface EntryPoint {
  icon: IconName;
  title: string;
  text: string;
  onClick: () => void;
}

/** Other ways in, under the main form: small round cards with an icon (the same links as before, new look). */
export function EntryPoints({ items }: { items: EntryPoint[] }) {
  return (
    <ul className={styles.entries}>
      {items.map((item) => (
        <li key={item.title}>
          <button type="button" className={styles.entry} onClick={item.onClick}>
            <span className={styles.entryIcon}><Icon name={item.icon} size={18} /></span>
            <span className={styles.entryText}><small>{item.text}</small>{item.title}</span>
            <Icon name="arrowRight" size={16} className={styles.entryArrow} />
          </button>
        </li>
      ))}
    </ul>
  );
}

export function Divider({ children }: { children: ReactNode }) {
  return <div className={styles.divider}>{children}</div>;
}

export function Alert({ kind, children }: { kind: 'error' | 'success'; children: ReactNode }) {
  return (
    <p className={kind === 'error' ? styles.error : styles.success} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </p>
  );
}
