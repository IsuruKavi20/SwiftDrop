import { getDeliveryDetails } from '../api/dispatcherApi';
import type { DispatchAddress, DispatchDelivery } from '../api/dispatcherTypes';
import { formatDateTime, formatFee, statusLabels, totalWeight } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { SourceBadge, StatusBadge } from './Badges';
import { DispatcherIcon } from './DispatcherIcon';
import { Modal } from './Modal';
import styles from './Dispatcher.module.css';

const ASSIGNABLE = new Set(['CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED']);

interface DeliveryDetailsDialogProps {
  deliveryId: number;
  onClose: () => void;
  /** Opens the assign/reassign dialog for this delivery. */
  onAssign: (delivery: DispatchDelivery) => void;
}

function Address({ label, address }: { label: string; address: DispatchAddress }) {
  return (
    <div className={styles.addressCard}>
      <small>{label}</small>
      <strong>{address.contactName}</strong>
      <span>{address.street}, {address.city}</span>
      <span>{address.district} district{address.postalCode ? ` · ${address.postalCode}` : ''}</span>
      <a href={`tel:${address.contactPhone}`}>{address.contactPhone}</a>
    </div>
  );
}

export function DeliveryDetailsDialog({ deliveryId, onClose, onAssign }: DeliveryDetailsDialogProps) {
  const { data: delivery, error, loading } = useDispatchData(() => getDeliveryDetails(deliveryId), [deliveryId]);
  const events = delivery ? [...delivery.trackingEvents].reverse() : [];
  const canAssign = delivery && ASSIGNABLE.has(delivery.status);

  return (
    <Modal wide title={delivery ? delivery.trackingNumber : 'Delivery details'}
      subtitle={delivery && <>Booked {formatDateTime(delivery.createdAt)} · {delivery.deliveryType.replace('_', ' ').toLowerCase()} delivery</>}
      onClose={onClose}
      footer={<>
        <button className={styles.secondaryButton} type="button" onClick={onClose}>Close</button>
        {canAssign && (
          <button className={styles.primaryButton} type="button" onClick={() => onAssign(delivery)}>
            <DispatcherIcon name={delivery.courier ? 'reassign' : 'assign'} size={18} />
            {delivery.courier ? 'Reassign courier' : 'Assign courier'}
          </button>
        )}
      </>}>
      {loading && !delivery && <p className={styles.muted} role="status">Loading delivery…</p>}
      {error && <p className={styles.errorBox} role="alert">{error}</p>}
      {delivery && (
        <div className={styles.details}>
          <dl className={styles.factRow}>
            <div><dt>Status</dt><dd><StatusBadge status={delivery.status} /></dd></div>
            <div><dt>Courier</dt><dd>{delivery.courier?.name ?? 'Not assigned'}</dd></div>
            <div><dt>Fee</dt><dd>{formatFee(delivery.estimatedFee)}</dd></div>
            <div><dt>Booked by</dt><dd><SourceBadge source={delivery.source} /></dd></div>
          </dl>

          <div className={styles.addressPair}>
            <Address label="Pickup" address={delivery.pickup} />
            <Address label="Destination" address={delivery.destination} />
          </div>

          {delivery.specialInstructions && (
            <p className={styles.infoBox}><strong>Instructions:</strong> {delivery.specialInstructions}</p>
          )}

          <section>
            <h3>Parcels <span className={styles.muted}>({totalWeight(delivery.parcels)} total)</span></h3>
            <ul className={styles.parcelList}>
              {delivery.parcels.map((p) => (
                <li key={p.id}>
                  <strong>{p.description}</strong>
                  <span>{p.weightKg} kg · {p.lengthCm} × {p.widthCm} × {p.heightCm} cm</span>
                </li>
              ))}
            </ul>
          </section>

          <div className={styles.detailColumns}>
            <section>
              <h3>Tracking history</h3>
              <ol className={styles.timeline}>
                {events.map((e, index) => (
                  <li key={`${e.eventTime}-${index}`} data-status={e.status}>
                    <strong>{statusLabels[e.status]}</strong>
                    <span>{e.description}</span>
                    <small>{formatDateTime(e.eventTime)}{e.locationName ? ` · ${e.locationName}` : ''}</small>
                  </li>
                ))}
              </ol>
            </section>

            <section>
              <h3>Courier assignments</h3>
              {delivery.assignments.length === 0 ? (
                <p className={styles.muted}>No courier has been assigned yet.</p>
              ) : (
                <ol className={styles.assignmentList}>
                  {delivery.assignments.map((a) => (
                    <li key={a.assignmentId} data-active={a.active || undefined}>
                      <div>
                        <strong>{a.courierName}</strong>
                        {a.active && <span className={styles.currentTag}>Current</span>}
                      </div>
                      <small>Assigned {formatDateTime(a.assignedAt)} by {a.assignedByName}</small>
                      {!a.active && a.endedAt && (
                        <small>Ended {formatDateTime(a.endedAt)} by {a.endedByName}{a.endReason ? `: ${a.endReason}` : ''}</small>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </div>
      )}
    </Modal>
  );
}
