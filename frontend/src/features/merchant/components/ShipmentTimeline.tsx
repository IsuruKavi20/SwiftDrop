import { MerchantIcon } from './MerchantIcon';
import styles from './ShipmentTimeline.module.css';

interface TrackingEvent {
  id?: number;
  status: string;
  description?: string | null;
  locationName?: string | null;
  eventTime: string;
  recordedByName?: string | null;
}

function shipmentStatusLabel(status: string) {
  const label = status.toLowerCase().replaceAll('_', ' ');
  return label.charAt(0).toUpperCase() + label.slice(1);
}

export function ShipmentTimeline({ events, recordedByLabel = 'Recorded by' }: {
  events: TrackingEvent[];
  recordedByLabel?: string;
}) {
  if (events.length === 0) return <p className={styles.empty}>No tracking events recorded yet.</p>;

  return (
    <ol className={styles.timeline}>
      {events.map((event, index) => (
        <li key={event.id ?? `${event.eventTime}-${index}`} className={styles.event}>
          <span className={styles.marker}><MerchantIcon name="pin" size={15} /></span>
          <div>
            <strong>{shipmentStatusLabel(event.status)}</strong>
            {event.description && <p>{event.description}</p>}
            <div className={styles.eventMeta}>
              <time dateTime={event.eventTime}>{new Date(event.eventTime).toLocaleString()}</time>
              {event.locationName && <span>{event.locationName}</span>}
              {event.recordedByName && <span>{recordedByLabel} {event.recordedByName}</span>}
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
