import { useEffect, useState } from 'react';
import type { DispatchDelivery } from '../api/dispatcherTypes';
import { AssignCourierDialog } from '../components/AssignCourierDialog';
import { DeliveryDetailsDialog } from '../components/DeliveryDetailsDialog';
import styles from '../components/Dispatcher.module.css';

/**
 * The details and assign dialogs every dispatcher list shares, plus the confirmation message after an assignment.
 * Render `dialogs` once in the page.
 */
export function useDeliveryDialogs() {
  const [detailsId, setDetailsId] = useState<number>();
  const [assigning, setAssigning] = useState<DispatchDelivery>();
  const [message, setMessage] = useState<string>();

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(undefined), 5000);
    return () => clearTimeout(timer);
  }, [message]);

  const dialogs = (
    <>
      {detailsId !== undefined && !assigning && (
        <DeliveryDetailsDialog deliveryId={detailsId} onClose={() => setDetailsId(undefined)} onAssign={setAssigning} />
      )}
      {assigning && (
        <AssignCourierDialog delivery={assigning} onClose={() => setAssigning(undefined)}
          onAssigned={(text) => { setAssigning(undefined); setMessage(text); }} />
      )}
      <div className={styles.toastArea} aria-live="polite">
        {message && <p className={styles.toast}>{message}</p>}
      </div>
    </>
  );

  return { openDetails: setDetailsId, openAssign: setAssigning, dialogs };
}
