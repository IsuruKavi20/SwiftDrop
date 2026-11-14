import { useEffect, useState } from 'react';
import {
  getMerchantDelivery,
  type DeliveryStatus,
  type MerchantDeliveryDetail,
} from '../api/merchantOrdersApi';
import styles from './MerchantDashboard.module.css';

interface OrderDetailsModalProps {
  deliveryId: number | null;
  onClose: () => void;
}

const STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

const STATUS_TONE: Record<DeliveryStatus, string> = {
  PENDING: styles.toneNeutral,
  CONFIRMED: styles.toneInfo,
  PICKED_UP: styles.toneInfo,
  IN_TRANSIT: styles.toneInfo,
  OUT_FOR_DELIVERY: styles.toneInfo,
  DELIVERED: styles.toneSuccess,
  FAILED: styles.toneError,
  CANCELLED: styles.toneNeutral,
};

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  } catch {
    return iso;
  }
}

export function OrderDetailsModal({ deliveryId, onClose }: OrderDetailsModalProps) {
  const [prevDeliveryId, setPrevDeliveryId] = useState<number | null>(deliveryId);
  const [loading, setLoading] = useState<boolean>(true);
  const [detail, setDetail] = useState<MerchantDeliveryDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  if (deliveryId !== prevDeliveryId) {
    setPrevDeliveryId(deliveryId);
    setLoading(true);
    setDetail(null);
    setError(null);
  }

  useEffect(() => {
    if (!deliveryId) return;

    let active = true;

    getMerchantDelivery(deliveryId)
      .then((data) => {
        if (active) {
          setDetail(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : 'Failed to load order details');
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [deliveryId]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  if (!deliveryId) return null;

  async function handleCopyTracking() {
    if (!detail?.trackingNumber) return;
    try {
      await navigator.clipboard.writeText(detail.trackingNumber);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore clipboard error
    }
  }

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-order-title"
    >
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <div className={styles.modalHeaderLeft}>
            <span id="modal-order-title" className={`${styles.mono} ${styles.sectionTitle}`}>
              {detail ? detail.trackingNumber : 'Loading order…'}
            </span>
            {detail && (
              <>
                <button
                  type="button"
                  className={styles.ghostButton}
                  style={{ padding: '3px 8px', fontSize: '12px' }}
                  onClick={handleCopyTracking}
                  title="Copy tracking number"
                >
                  {copied ? 'Copied!' : 'Copy'}
                </button>
                <span className={`${styles.badge} ${STATUS_TONE[detail.status]}`}>
                  {STATUS_LABELS[detail.status]}
                </span>
                <span className={styles.typeBadge}>{detail.deliveryType}</span>
              </>
            )}
          </div>
          <button
            type="button"
            className={styles.modalCloseBtn}
            onClick={onClose}
            aria-label="Close details"
          >
            ×
          </button>
        </div>

        <div className={styles.modalBody}>
          {loading && (
            <div className={styles.emptyState}>
              <p>Loading delivery information…</p>
            </div>
          )}

          {error && (
            <div className={styles.emptyState}>
              <p style={{ color: 'var(--color-error)' }}>{error}</p>
              <button
                type="button"
                className={styles.ghostButton}
                style={{ marginTop: '12px' }}
                onClick={onClose}
              >
                Close
              </button>
            </div>
          )}

          {!loading && !error && detail && (
            <>
              {/* Addresses section */}
              <div className={styles.modalSection}>
                <h4 className={styles.modalSectionTitle}>Addresses</h4>
                <div className={styles.addressGrid}>
                  <div className={styles.addressCard}>
                    <div className={styles.addressCardTitle}>Pickup Location</div>
                    <strong>{detail.pickup.contactName}</strong> ({detail.pickup.contactPhone})
                    <div>{detail.pickup.street}</div>
                    <div>
                      {detail.pickup.city}, {detail.pickup.district}
                      {detail.pickup.postalCode ? ` ${detail.pickup.postalCode}` : ''}
                    </div>
                  </div>

                  <div className={styles.addressCard}>
                    <div className={styles.addressCardTitle}>Destination / Recipient</div>
                    <strong>{detail.destination.contactName}</strong> ({detail.destination.contactPhone})
                    <div>{detail.destination.street}</div>
                    <div>
                      {detail.destination.city}, {detail.destination.district}
                      {detail.destination.postalCode ? ` ${detail.destination.postalCode}` : ''}
                    </div>
                  </div>
                </div>
              </div>

              {/* Special Instructions if any */}
              {detail.specialInstructions && (
                <div className={styles.modalSection}>
                  <h4 className={styles.modalSectionTitle}>Special Instructions</h4>
                  <p style={{ margin: 0, fontSize: '13px', fontStyle: 'italic', background: 'var(--color-cream)', padding: '10px 14px', borderRadius: 'var(--radius-input)' }}>
                    "{detail.specialInstructions}"
                  </p>
                </div>
              )}

              {/* Parcels */}
              <div className={styles.modalSection}>
                <h4 className={styles.modalSectionTitle}>
                  Parcels ({detail.parcels.length})
                </h4>
                <div className={styles.parcelList}>
                  {detail.parcels.map((parcel, idx) => (
                    <div key={parcel.id ?? idx} className={styles.parcelItem}>
                      <div>
                        <strong>{parcel.description}</strong>
                        <div style={{ color: 'var(--color-text-muted)', fontSize: '12px' }}>
                          Dimensions: {parcel.lengthCm} × {parcel.widthCm} × {parcel.heightCm} cm
                        </div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div>{parcel.weightKg} kg</div>
                        {parcel.declaredValue != null && (
                          <div style={{ color: 'var(--color-text-muted)', fontSize: '12px' }}>
                            Valued at LKR {parcel.declaredValue.toFixed(2)}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Dates */}
              <div className={styles.modalSection}>
                <h4 className={styles.modalSectionTitle}>Key Dates</h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', fontSize: '13px' }}>
                  <div>
                    <span style={{ color: 'var(--color-text-muted)' }}>Booked:</span>{' '}
                    <strong>{formatDate(detail.createdAt)}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-text-muted)' }}>Estimated Delivery:</span>{' '}
                    <strong>{formatDate(detail.estimatedDeliveryDate)}</strong>
                  </div>
                  {detail.deliveredAt && (
                    <div>
                      <span style={{ color: 'var(--color-text-muted)' }}>Delivered:</span>{' '}
                      <strong>{formatDate(detail.deliveredAt)}</strong>
                    </div>
                  )}
                </div>
              </div>

              {/* Tracking Timeline */}
              <div className={styles.modalSection}>
                <h4 className={styles.modalSectionTitle}>Tracking Timeline</h4>
                {detail.trackingEvents.length === 0 ? (
                  <p style={{ fontSize: '13px', color: 'var(--color-text-muted)', margin: 0 }}>
                    No tracking events recorded yet.
                  </p>
                ) : (
                  <div className={styles.timeline}>
                    {detail.trackingEvents.map((event, idx) => (
                      <div key={event.id ?? idx} className={styles.timelineItem}>
                        <div className={styles.timelineDot} />
                        <div className={styles.timelineStatus}>
                          {STATUS_LABELS[event.status] ?? event.status}
                        </div>
                        <div className={styles.timelineDesc}>{event.description}</div>
                        <div className={styles.timelineMeta}>
                          {formatDate(event.eventTime)}
                          {event.locationName ? ` • ${event.locationName}` : ''}
                          {event.recordedByName ? ` • Recorded by ${event.recordedByName}` : ''}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
