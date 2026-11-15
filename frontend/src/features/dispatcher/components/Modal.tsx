import { useEffect, useId, useRef, type ReactNode } from 'react';
import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

interface ModalProps {
  title: string;
  subtitle?: ReactNode;
  wide?: boolean;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

/** A native <dialog>: focus is trapped and Escape closes it without extra code. */
export function Modal({ title, subtitle, wide, onClose, children, footer }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => dialog?.close();
  }, []);

  return (
    <dialog ref={ref} className={styles.modal} data-wide={wide || undefined} aria-labelledby={titleId}
      onCancel={(event) => { event.preventDefault(); onClose(); }}
      onClick={(event) => { if (event.target === ref.current) onClose(); }}>
      <div className={styles.modalInner}>
        <header className={styles.modalHeader}>
          <div>
            <h2 id={titleId}>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button className={styles.iconButton} type="button" onClick={onClose} aria-label="Close">
            <DispatcherIcon name="close" />
          </button>
        </header>
        <div className={styles.modalBody}>{children}</div>
        {footer && <footer className={styles.modalFooter}>{footer}</footer>}
      </div>
    </dialog>
  );
}
