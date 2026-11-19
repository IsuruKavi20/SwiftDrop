import { useEffect, useId, useRef, useState } from 'react';
import { confirmMerchantDelivery, formatMerchantFee, type MerchantDeliveryDetail } from '../api/merchantOrdersApi';
import buttons from '../pages/MerchantOrders.module.css';
import styles from './ConfirmMerchantOrder.module.css';

export function ConfirmMerchantOrder({ order, onConfirmed }: {
  order: MerchantDeliveryDetail;
  onConfirmed: (order: MerchantDeliveryDetail) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const pending = useRef(false);
  const title = useId();
  const [reviewing, setReviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (reviewing && !dialog.current?.open) dialog.current?.showModal();
  }, [reviewing]);

  async function confirm() {
    if (pending.current) return;
    pending.current = true;
    setSubmitting(true);
    setError(null);
    try {
      const confirmed = await confirmMerchantDelivery(order.id);
      dialog.current?.close();
      onConfirmed(confirmed);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not confirm this order. Try again.');
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  }

  if (order.status !== 'PENDING') return null;

  return (
    <>
      <button className={buttons.primaryButton} type="button" onClick={() => {
        setError(null);
        setReviewing(true);
      }}>Confirm Order</button>
      {reviewing && <dialog className={styles.dialog} ref={dialog} aria-labelledby={title}
        onKeyDown={(event) => { if (event.key === 'Escape') event.stopPropagation(); }}
        onClose={() => setReviewing(false)}
        onCancel={(event) => { if (pending.current) event.preventDefault(); }}>
        <h3 id={title}>Confirm shipment {order.trackingNumber}</h3>
        <p>Confirm this shipment to make it available for courier assignment.</p>
        <dl>
          <div><dt>Pickup</dt><dd>{order.pickup.contactName}, {order.pickup.street}, {order.pickup.city}</dd></div>
          <div><dt>Delivery</dt><dd>{order.destination.contactName}, {order.destination.street}, {order.destination.city}</dd></div>
          <div><dt>Service</dt><dd>{order.deliveryType.replaceAll('_', ' ')}</dd></div>
          <div><dt>Parcels</dt><dd>{order.parcels.length}</dd></div>
          <div><dt>Estimated delivery fee</dt><dd>{order.estimatedFee == null ? 'Not recorded' : formatMerchantFee(order.estimatedFee)}</dd></div>
        </dl>
        {error && <p role="alert" className={styles.error}>{error}</p>}
        <div className={buttons.actions}>
          <button className={styles.cancel} autoFocus type="button" disabled={submitting} onClick={() => dialog.current?.close()}>Back</button>
          <button className={buttons.primaryButton} type="button" disabled={submitting} onClick={() => void confirm()}>
            {submitting ? 'Confirming…' : 'Confirm Order'}
          </button>
        </div>
      </dialog>}
    </>
  );
}
