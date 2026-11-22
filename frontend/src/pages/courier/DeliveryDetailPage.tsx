import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { TopHeader } from '../../components/layout/TopHeader';
import { StatusBadge } from '../../components/common/Badge';
import {
  ArrowLeftIcon,
  LocationPinIcon,
  PhoneIcon,
  CheckCircleIcon,
  AlertCircleIcon,
  ClockIcon,
  CopyIcon,
  CheckIcon,
  TruckIcon,
  ShieldCheckIcon,
  NavigationIcon,
} from '../../components/common/Icons';
import { courierService } from '../../services/courier/courierService';
import type { DeliveryDetails, ParcelStatus } from '../../types/courier';

function getPermittedNextStatuses(current?: ParcelStatus): ParcelStatus[] {
  switch (current) {
    case 'PENDING':
    case 'CONFIRMED':
      return ['PICKED_UP'];
    case 'PICKED_UP':
      return ['IN_TRANSIT'];
    case 'IN_TRANSIT':
      return ['OUT_FOR_DELIVERY'];
    case 'OUT_FOR_DELIVERY':
      return ['IN_TRANSIT'];
    case 'FAILED':
      return ['OUT_FOR_DELIVERY', 'IN_TRANSIT'];
    default:
      return ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];
  }
}

const STEPS: { status: ParcelStatus; label: string }[] = [
  { status: 'CONFIRMED', label: 'Confirmed' },
  { status: 'PICKED_UP', label: 'Picked Up' },
  { status: 'IN_TRANSIT', label: 'In Transit' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { status: 'DELIVERED', label: 'Delivered' },
];

export const DeliveryDetailPage: React.FC = () => {
  const { trackingNumber } = useParams<{ trackingNumber: string }>();
  const navigate = useNavigate();

  const [delivery, setDelivery] = useState<DeliveryDetails | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Status Action Form State
  const [actionType, setActionType] = useState<'NONE' | 'ADVANCE' | 'CONFIRM' | 'FAIL'>('NONE');
  const [nextStatus, setNextStatus] = useState<ParcelStatus>('IN_TRANSIT');
  const [actionNotes, setActionNotes] = useState<string>('');
  const [signName, setSignName] = useState<string>('');
  const [failureReason, setFailureReason] = useState<string>('RECIPIENT_UNAVAILABLE');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [copiedTracking, setCopiedTracking] = useState<boolean>(false);
  const [copiedAddress, setCopiedAddress] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    async function fetchDetails() {
      if (!trackingNumber) return;

      try {
        setLoading(true);
        const data = await courierService.getDeliveryDetails(trackingNumber);
        if (isMounted) {
          if (!data) {
            setError(`Parcel with tracking number "${trackingNumber}" was not found.`);
          } else {
            setDelivery(data);
            if (data.status === 'PENDING' || data.status === 'CONFIRMED') {
              setNextStatus('PICKED_UP');
            } else if (data.status === 'PICKED_UP') {
              setNextStatus('IN_TRANSIT');
            } else if (data.status === 'IN_TRANSIT') {
              setNextStatus('OUT_FOR_DELIVERY');
            } else if (data.status === 'FAILED') {
              setNextStatus('OUT_FOR_DELIVERY');
            } else if (data.status === 'OUT_FOR_DELIVERY') {
              setNextStatus('IN_TRANSIT');
            }
          }
        }
      } catch (err: unknown) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Error fetching delivery details.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    fetchDetails();

    return () => {
      isMounted = false;
    };
  }, [trackingNumber]);

  const handleCopyTracking = () => {
    if (!delivery) return;
    navigator.clipboard.writeText(delivery.trackingNumber);
    setCopiedTracking(true);
    setTimeout(() => setCopiedTracking(false), 2000);
  };

  const handleCopyAddress = () => {
    if (!delivery) return;
    const full = `${delivery.deliveryAddress}, ${delivery.city} ${delivery.postalCode}`;
    navigator.clipboard.writeText(full);
    setCopiedAddress(true);
    setTimeout(() => setCopiedAddress(false), 2000);
  };

  const handleAdvanceStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!delivery) return;

    try {
      setSubmitting(true);
      setActionError(null);
      const updated = await courierService.updateDeliveryStatus(delivery.trackingNumber, {
        nextStatus,
        notes: actionNotes,
      });
      setDelivery(updated);
      setActionSuccess(`Status advanced to ${nextStatus.replace(/_/g, ' ')}!`);
      setActionType('NONE');
      setActionNotes('');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update status.';
      setActionError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmDelivery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!delivery) return;

    if (!signName.trim()) {
      setActionError('Recipient confirmation signature name is required.');
      return;
    }

    try {
      setSubmitting(true);
      setActionError(null);
      const updated = await courierService.confirmDelivery(delivery.trackingNumber, {
        recipientSignName: signName,
        deliveryNotes: actionNotes,
      });
      setDelivery(updated);
      setActionSuccess('Delivery confirmed and signature proof logged successfully!');
      setActionType('NONE');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to confirm delivery.';
      setActionError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleReportFailure = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!delivery) return;

    if (!actionNotes.trim()) {
      setActionError('Detailed failure remarks are mandatory.');
      return;
    }

    try {
      setSubmitting(true);
      setActionError(null);
      const updated = await courierService.reportFailure(delivery.trackingNumber, {
        failureReason,
        failureRemarks: actionNotes,
      });
      setDelivery(updated);
      setActionSuccess('Delivery marked as failed. Dispatcher notified.');
      setActionType('NONE');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to report failure.';
      setActionError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="delivery-detail-skeleton">
        <div className="skeleton-row shimmer" style={{ height: '50px', marginBottom: '20px' }} />
        <div className="skeleton-row shimmer" style={{ height: '140px', marginBottom: '20px' }} />
        <div className="skeleton-stats-grid">
          <div className="skeleton-card shimmer" style={{ height: '260px' }} />
          <div className="skeleton-card shimmer" style={{ height: '260px' }} />
        </div>
      </div>
    );
  }

  if (error || !delivery) {
    return (
      <div className="dashboard-error-card">
        <div className="dashboard-error-icon">
          <AlertCircleIcon size={32} color="#EF4444" />
        </div>
        <h2 className="dashboard-error-title">Parcel Record Unavailable</h2>
        <p className="dashboard-error-text">
          {error || 'The requested parcel could not be found or you do not have permission to view it.'}
        </p>
        <button
          type="button"
          className="btn-action primary"
          onClick={() => navigate('/courier/deliveries')}
        >
          <ArrowLeftIcon size={16} />
          <span>Return to My Deliveries</span>
        </button>
      </div>
    );
  }

  const isDelivered = delivery.status === 'DELIVERED';
  const isFailed = delivery.status === 'FAILED';
  const isCompleted = isDelivered || isFailed;

  // Compute current step index
  const currentStepIdx = STEPS.findIndex((s) => s.status === delivery.status);

  return (
    <>
      <TopHeader
        title={`Parcel ${delivery.trackingNumber}`}
        subtitle={`Scheduled delivery to ${delivery.recipientName} • ${delivery.city}`}
      />

      {/* Navigation Breadcrumb Bar */}
      <div className="detail-breadcrumb-bar">
        <Link to="/courier/deliveries" className="detail-back-link">
          <ArrowLeftIcon size={16} />
          <span>Back to Deliveries</span>
        </Link>
        <div className="detail-header-status-wrap">
          <span className="detail-assigned-label">ASSIGNED ROUTE</span>
          <StatusBadge status={delivery.status} size="md" />
        </div>
      </div>

      {/* Floating Toast Notification */}
      {actionSuccess && (
        <div className="detail-toast-alert success">
          <div className="toast-left">
            <CheckCircleIcon size={18} color="#10B981" />
            <span>{actionSuccess}</span>
          </div>
          <button
            type="button"
            className="toast-close-btn"
            onClick={() => setActionSuccess(null)}
            aria-label="Dismiss alert"
          >
            ✕
          </button>
        </div>
      )}

      {actionError && (
        <div className="detail-toast-alert error">
          <div className="toast-left">
            <AlertCircleIcon size={18} color="#EF4444" />
            <span>{actionError}</span>
          </div>
          <button
            type="button"
            className="toast-close-btn"
            onClick={() => setActionError(null)}
            aria-label="Dismiss alert"
          >
            ✕
          </button>
        </div>
      )}

      {/* Hero Tracking Card with Stepper */}
      <div className="content-card detail-hero-card">
        <div className="detail-hero-top">
          <div className="detail-tracking-headline">
            <div className="tracking-code-large">
              <span>{delivery.trackingNumber}</span>
              <button
                type="button"
                className={`btn-copy-lg ${copiedTracking ? 'copied' : ''}`}
                onClick={handleCopyTracking}
                title={copiedTracking ? 'Copied!' : 'Copy tracking code'}
              >
                {copiedTracking ? (
                  <>
                    <CheckIcon size={14} color="#10B981" />
                    <span>Copied!</span>
                  </>
                ) : (
                  <>
                    <CopyIcon size={14} />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>
            <span className="detail-assigned-time">Assigned at: {delivery.assignedAt || 'Recently assigned'}</span>
          </div>

          <div className="detail-quick-actions">
            {!isCompleted ? (
              <>
                <button
                  type="button"
                  className="btn-action primary btn-action-lg"
                  onClick={() => setActionType('ADVANCE')}
                >
                  <TruckIcon size={16} />
                  <span>Update Status</span>
                </button>
                <button
                  type="button"
                  className="btn-action btn-confirm-lg"
                  onClick={() => setActionType('CONFIRM')}
                >
                  <CheckCircleIcon size={16} color="#FFFFFF" />
                  <span>Confirm Delivered</span>
                </button>
                <button
                  type="button"
                  className="btn-action btn-fail-lg"
                  onClick={() => setActionType('FAIL')}
                >
                  <AlertCircleIcon size={16} color="#DC2626" />
                  <span>Report Failure</span>
                </button>
              </>
            ) : (
              <div className="detail-completed-badge-card">
                {isDelivered ? (
                  <span className="completed-tag delivered">
                    <CheckCircleIcon size={16} color="#059669" />
                    Delivery Completed & Signed
                  </span>
                ) : (
                  <span className="completed-tag failed">
                    <AlertCircleIcon size={16} color="#DC2626" />
                    Delivery Attempt Failed
                  </span>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Visual Lifecycle Stepper Pipeline */}
        <div className="lifecycle-stepper-container" aria-label="Delivery progress timeline">
          <div className="stepper-track">
            {STEPS.map((step, idx) => {
              const isPast = currentStepIdx >= idx;
              const isCurrent = currentStepIdx === idx;
              const isFailedStep = isFailed && idx === currentStepIdx;

              return (
                <div
                  key={step.status}
                  className={`stepper-step ${isPast ? 'past' : ''} ${isCurrent ? 'current' : ''} ${isFailedStep ? 'failed-node' : ''}`}
                >
                  <div className="stepper-dot">
                    {isFailedStep ? (
                      <AlertCircleIcon size={14} color="#FFFFFF" />
                    ) : isPast ? (
                      <CheckIcon size={14} color={isCurrent ? '#181B20' : '#FFFFFF'} />
                    ) : (
                      <span className="step-num">{idx + 1}</span>
                    )}
                  </div>
                  <span className="stepper-label">{step.label}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Main Details Grid: Recipient Card + Audit & Proof */}
      <div className="detail-columns-grid">
        {/* Left Column: Destination & Package Specifications */}
        <div className="detail-col">
          <div className="content-card detail-spec-card">
            <h2 className="card-title">Recipient & Delivery Destination</h2>

            <div className="detail-info-block">
              <span className="spec-label">Recipient Name</span>
              <span className="spec-value-large">{delivery.recipientName}</span>
            </div>

            <div className="detail-info-block">
              <span className="spec-label">Contact Number</span>
              <a href={`tel:${delivery.recipientPhone}`} className="spec-phone-link">
                <PhoneIcon size={15} />
                <span>{delivery.recipientPhone}</span>
              </a>
            </div>

            <div className="detail-info-block">
              <div className="spec-label-row">
                <span className="spec-label">Delivery Address</span>
                <div className="spec-address-actions">
                  <button
                    type="button"
                    className="spec-copy-link"
                    onClick={handleCopyAddress}
                    title="Copy destination address"
                  >
                    {copiedAddress ? 'Address Copied!' : 'Copy Address'}
                  </button>
                  <a
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                      `${delivery.deliveryAddress}, ${delivery.city} ${delivery.postalCode}`
                    )}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="spec-nav-link"
                    title="Navigate via Google Maps"
                  >
                    <NavigationIcon size={13} />
                    <span>Navigate</span>
                  </a>
                </div>
              </div>
              <div className="spec-address-box">
                <LocationPinIcon size={16} color="var(--primary-active)" className="address-box-pin" />
                <span className="spec-address-text">
                  {delivery.deliveryAddress}, {delivery.city} ({delivery.postalCode})
                </span>
              </div>
            </div>

            {delivery.specialInstructions && (
              <div className="spec-instructions-alert">
                <span className="instructions-tag">Customer Special Instructions</span>
                <p className="instructions-body">{delivery.specialInstructions}</p>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Evidence or Audit History */}
        <div className="detail-col">
          {/* Delivered Proof Card */}
          {isDelivered && delivery.proof && (
            <div className="content-card proof-record-card">
              <div className="proof-header-row">
                <div className="proof-icon-badge">
                  <ShieldCheckIcon size={20} color="#059669" />
                </div>
                <div>
                  <h3 className="proof-title">Verified Proof of Delivery</h3>
                  <span className="proof-sub">Handover confirmed by recipient</span>
                </div>
              </div>
              <div className="proof-body-grid">
                <div>
                  <span className="proof-field-label">Signee Name:</span>
                  <span className="proof-field-val">{delivery.proof.recipientSignName}</span>
                </div>
                <div>
                  <span className="proof-field-label">Verification Type:</span>
                  <span className="proof-field-val">{delivery.proof.proofType}</span>
                </div>
                {delivery.proof.deliveryNotes && (
                  <div style={{ gridColumn: 'span 2' }}>
                    <span className="proof-field-label">Courier Remarks:</span>
                    <span className="proof-field-val">{delivery.proof.deliveryNotes}</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Failed Delivery Incident Card */}
          {isFailed && delivery.failure && (
            <div className="content-card failure-incident-card">
              <div className="failure-header-row">
                <div className="failure-icon-badge">
                  <AlertCircleIcon size={20} color="#DC2626" />
                </div>
                <div>
                  <h3 className="failure-title">Delivery Failure Logged</h3>
                  <span className="failure-sub">Issue flagged for dispatch re-routing</span>
                </div>
              </div>
              <div className="failure-body-content">
                <div className="failure-reason-pill">
                  Reason: <strong>{delivery.failure.failureReason.replace(/_/g, ' ')}</strong>
                </div>
                <p className="failure-remarks-text">"{delivery.failure.failureRemarks}"</p>
                <span className="failure-time-text">Logged on: {delivery.failure.reportedAt || 'Today'}</span>
              </div>
            </div>
          )}

          {/* Status Audit History Card */}
          <div className="content-card status-audit-card">
            <h2 className="card-title">Status Audit Trail</h2>
            {delivery.statusHistory.length === 0 ? (
              <p className="audit-empty-text">No lifecycle status events recorded yet.</p>
            ) : (
              <div className="timeline-list audit-timeline">
                {delivery.statusHistory.map((item) => (
                  <div key={item.id} className="timeline-item">
                    <div className="timeline-icon-badge timeline-gold">
                      <ClockIcon size={14} color="#181B20" />
                    </div>
                    <div className="timeline-content">
                      <div className="audit-step-title">
                        {item.fromStatus ? (
                          <>
                            <span className="audit-prev-status">{item.fromStatus}</span>
                            <span className="audit-arrow">→</span>
                          </>
                        ) : null}
                        <span className="audit-next-status">{item.toStatus}</span>
                      </div>
                      <span className="timeline-sub">
                        By {item.changedBy} • {item.timestamp}
                      </span>
                      {item.notes && <p className="audit-notes-box">{item.notes}</p>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Modern Modal Dialogs */}

      {/* Modal 1: Advance Status */}
      {actionType === 'ADVANCE' && (
        <div className="modal-backdrop" onClick={() => setActionType('NONE')}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3 className="modal-title">Advance Parcel Status</h3>
                <span className="modal-subtitle">Transition delivery along the verified lifecycle graph</span>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setActionType('NONE')}
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleAdvanceStatus} className="modal-form">
              <div className="form-group">
                <label className="form-label">Next Operational Status *</label>
                <select
                  value={nextStatus}
                  onChange={(e) => setNextStatus(e.target.value as ParcelStatus)}
                  className="form-select"
                >
                  {getPermittedNextStatuses(delivery?.status).map((st) => (
                    <option key={st} value={st}>
                      {st.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Status Notes / Location (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Arrived at Colombo 03 distribution hub"
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="form-input"
                />
              </div>

              <div className="modal-actions-footer">
                <button
                  type="button"
                  className="btn-action secondary"
                  onClick={() => setActionType('NONE')}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="btn-action primary"
                >
                  {submitting ? 'Saving Progress...' : 'Confirm Status Transition'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 2: Confirm Delivered with Proof */}
      {actionType === 'CONFIRM' && (
        <div className="modal-backdrop" onClick={() => setActionType('NONE')}>
          <div className="modal-window modal-window-confirm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-header-icon-wrap green">
                <CheckCircleIcon size={22} color="#059669" />
              </div>
              <div>
                <h3 className="modal-title">Confirm Delivery</h3>
                <span className="modal-subtitle">Capture recipient signature name and finalize handover</span>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setActionType('NONE')}
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleConfirmDelivery} className="modal-form">
              <div className="form-group">
                <label className="form-label">Recipient Full Name (Signee) *</label>
                <input
                  type="text"
                  required
                  placeholder="Enter the name of the individual who accepted the package"
                  value={signName}
                  onChange={(e) => setSignName(e.target.value)}
                  className="form-input"
                  autoFocus
                />
              </div>

              <div className="form-group">
                <label className="form-label">Delivery Notes (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Left with building reception at front desk"
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="form-input"
                />
              </div>

              <div className="modal-actions-footer">
                <button
                  type="button"
                  className="btn-action secondary"
                  onClick={() => setActionType('NONE')}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="btn-action btn-confirm-submit"
                >
                  {submitting ? 'Verifying & Saving...' : 'Confirm & Complete Delivery'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 3: Report Delivery Failure */}
      {actionType === 'FAIL' && (
        <div className="modal-backdrop" onClick={() => setActionType('NONE')}>
          <div className="modal-window modal-window-fail" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-header-icon-wrap red">
                <AlertCircleIcon size={22} color="#DC2626" />
              </div>
              <div>
                <h3 className="modal-title">Report Delivery Failure</h3>
                <span className="modal-subtitle">Document attempt failure reason for dispatcher audit</span>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setActionType('NONE')}
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleReportFailure} className="modal-form">
              <div className="form-group">
                <label className="form-label">Primary Failure Reason *</label>
                <select
                  value={failureReason}
                  onChange={(e) => setFailureReason(e.target.value)}
                  className="form-select"
                >
                  <option value="RECIPIENT_UNAVAILABLE">Recipient Unavailable / Phone Unreachable</option>
                  <option value="INCORRECT_ADDRESS">Incorrect Delivery Address</option>
                  <option value="CUSTOMER_REJECTED">Customer Refused Package</option>
                  <option value="DAMAGED_PARCEL">Damaged Parcel Packaging</option>
                  <option value="ACCESS_RESTRICTED">Premises Access Restricted / Gate Locked</option>
                  <option value="WEATHER_DELAY">Severe Weather / Road Blockage</option>
                  <option value="OTHER">Other Operational Issue</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Detailed Incident Remarks *</label>
                <textarea
                  required
                  rows={3}
                  placeholder="Detail attempts made (e.g. called customer twice at 14:10, premise locked, no answer at front gate)..."
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="form-textarea"
                />
              </div>

              <div className="modal-actions-footer">
                <button
                  type="button"
                  className="btn-action secondary"
                  onClick={() => setActionType('NONE')}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="btn-action btn-fail-submit"
                >
                  {submitting ? 'Submitting Report...' : 'Log Failure & Notify Dispatch'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
};
