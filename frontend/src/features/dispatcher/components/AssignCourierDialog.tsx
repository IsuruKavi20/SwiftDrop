import { useState, type FormEvent } from 'react';
import { assignCourier, listAssignableCouriers } from '../api/dispatcherApi';
import type { DispatchDelivery } from '../api/dispatcherTypes';
import { parcelSummary } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { Modal } from './Modal';
import styles from './Dispatcher.module.css';

interface AssignCourierDialogProps {
  delivery: DispatchDelivery;
  onClose: () => void;
  /** Called after a successful (re)assignment with a message to show. */
  onAssigned: (message: string) => void;
}

/**
 * Assign a courier, or reassign one. Only AVAILABLE couriers can be chosen (CourierRepository.findAvailableForAssignment),
 * lightest workload first. Reassigning ends the current assignment, so it asks for the reason the backend stores.
 */
export function AssignCourierDialog({ delivery, onClose, onAssigned }: AssignCourierDialogProps) {
  const reassigning = delivery.courier !== null;
  const { data: couriers, error: loadError, loading } = useDispatchData(listAssignableCouriers, []);
  const [courierId, setCourierId] = useState<number>();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  const choices = couriers?.filter((c) => c.courierId !== delivery.courier?.courierId) ?? [];
  const reasonMissing = reassigning && !reason.trim();

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (courierId === undefined || reasonMissing) return;
    setSaving(true);
    setError(undefined);
    try {
      await assignCourier(delivery.id, courierId, reassigning ? reason : undefined);
      const name = couriers?.find((c) => c.courierId === courierId)?.name ?? 'The courier';
      onAssigned(`${name} is now assigned to ${delivery.trackingNumber}.`);
    } catch (reasonError) {
      setError(reasonError instanceof Error ? reasonError.message : 'Could not save the assignment. Please try again.');
      setSaving(false);
    }
  }

  const formId = `assign-${delivery.id}`;

  return (
    <Modal
      title={reassigning ? 'Reassign courier' : 'Assign courier'}
      subtitle={<><span className={styles.mono}>{delivery.trackingNumber}</span> · {delivery.pickup.city} → {delivery.destination.city} · {parcelSummary(delivery.parcels)}</>}
      onClose={onClose}
      footer={<>
        <button className={styles.secondaryButton} type="button" onClick={onClose}>Cancel</button>
        <button className={styles.primaryButton} type="submit" form={formId}
          disabled={saving || courierId === undefined || reasonMissing}>
          {saving ? 'Saving…' : reassigning ? 'Reassign' : 'Assign'}
        </button>
      </>}>
      <form id={formId} onSubmit={submit} className={styles.assignForm}>
        {reassigning && (
          <p className={styles.infoBox}>
            Currently assigned to <strong>{delivery.courier?.name}</strong>. Their assignment ends and stays in the delivery's history.
          </p>
        )}

        <fieldset className={styles.courierChoices}>
          <legend>Available couriers</legend>
          {loading && !couriers && <p className={styles.muted} role="status">Loading couriers…</p>}
          {loadError && <p className={styles.errorBox} role="alert">{loadError}</p>}
          {couriers && choices.length === 0 && (
            <p className={styles.muted}>No other courier is available right now. Couriers marked Busy or Offline can't be assigned.</p>
          )}
          {choices.map((c) => (
            <label key={c.courierId} className={styles.courierChoice}>
              <input type="radio" name="courier" value={c.courierId} checked={courierId === c.courierId}
                onChange={() => setCourierId(c.courierId)} />
              <span>
                <strong>{c.name}</strong>
                <small>{[c.vehicleType, c.vehicleNumber].filter(Boolean).join(' · ') || 'No vehicle recorded'}</small>
              </span>
              <span className={styles.choiceLoad}>
                {c.activeDeliveries} active {c.activeDeliveries === 1 ? 'delivery' : 'deliveries'}
              </span>
            </label>
          ))}
        </fieldset>

        {reassigning && (
          <label className={styles.field}>
            <span>Reason for reassignment</span>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} rows={3} required
              placeholder="e.g. Courier reported a vehicle breakdown" />
            <small>{reason.length}/500</small>
          </label>
        )}

        {error && <p className={styles.errorBox} role="alert">{error}</p>}
      </form>
    </Modal>
  );
}
