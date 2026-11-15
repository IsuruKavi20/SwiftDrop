import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { Modal } from './Modal';
import styles from './Dispatcher.module.css';

interface FormDialogProps {
  title: string;
  subtitle?: ReactNode;
  wide?: boolean;
  submitLabel: string;
  /** Styles the submit button as a destructive action (block, reject). */
  danger?: boolean;
  /** False keeps the submit button disabled (e.g. a required field is empty). */
  canSubmit?: boolean;
  onClose: () => void;
  /** Saves; a thrown Error's message is shown in the dialog. */
  onSubmit: () => Promise<void>;
  children: ReactNode;
}

/** A dialog with a form, Cancel / submit buttons, a saving state and the error from a failed save. */
export function FormDialog({ title, subtitle, wide, submitLabel, danger, canSubmit = true, onClose, onSubmit, children }: FormDialogProps) {
  const formId = useId();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit || saving) return;
    setSaving(true);
    setError(undefined);
    try {
      await onSubmit();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not save. Please try again.');
      setSaving(false);
    }
  }

  return (
    <Modal title={title} subtitle={subtitle} wide={wide} onClose={onClose}
      footer={<>
        <button className={styles.secondaryButton} type="button" onClick={onClose}>Cancel</button>
        <button className={danger ? styles.dangerButton : styles.primaryButton} type="submit" form={formId} disabled={saving || !canSubmit}>
          {saving ? 'Saving…' : submitLabel}
        </button>
      </>}>
      <form id={formId} onSubmit={submit} className={styles.assignForm}>
        {children}
        {error && <p className={styles.errorBox} role="alert">{error}</p>}
      </form>
    </Modal>
  );
}

/** A required reason (max 500 characters, like the auth service's audit reasons). */
export function ReasonField({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string }) {
  return (
    <label className={styles.field}>
      <span>{label}</span>
      <textarea value={value} onChange={(e) => onChange(e.target.value)} maxLength={500} rows={3} required placeholder={placeholder} />
      <small>{value.length}/500</small>
    </label>
  );
}
