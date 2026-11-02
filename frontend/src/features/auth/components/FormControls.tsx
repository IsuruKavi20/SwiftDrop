import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import arrowIcon from '../../../assets/auth/icon-arrow.svg';
import styles from './FormControls.module.css';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  icon: string;
}

/** Figma "Text Field": label above, an input box with an icon bubble. */
export function TextField({ label, icon, id, ...input }: TextFieldProps) {
  return (
    <label className={styles.field} htmlFor={id}>
      <span className={styles.label}>{label}</span>
      <span className={styles.inputBox}>
        <span className={styles.iconBubble}>
          <img src={icon} width={16} height={16} alt="" />
        </span>
        <input id={id} className={styles.input} {...input} />
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
      <span className={styles.googleMark}>G</span>
      Continue with Google
    </button>
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
