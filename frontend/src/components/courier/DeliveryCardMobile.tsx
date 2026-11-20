import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import type { DeliverySummary } from '../../types/courier';
import { StatusBadge } from '../common/Badge';
import { LocationPinIcon, EyeIcon, PencilIcon, CopyIcon, CheckIcon, PhoneIcon } from '../common/Icons';

interface DeliveryCardMobileProps {
  delivery: DeliverySummary;
}

export const DeliveryCardMobile: React.FC<DeliveryCardMobileProps> = ({ delivery }) => {
  const [copied, setCopied] = useState(false);
  const isCompleted = ['DELIVERED', 'FAILED', 'CANCELLED'].includes(delivery.status);

  const handleCopy = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(delivery.trackingNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <article className="delivery-card-mobile">
      <div className="mobile-card-header">
        <div className="mobile-tracking-group">
          <span className="mobile-tracking-code">{delivery.trackingNumber}</span>
          <button
            type="button"
            className="mobile-copy-btn"
            title="Copy Tracking Number"
            onClick={handleCopy}
          >
            {copied ? <CheckIcon size={12} color="#10B981" /> : <CopyIcon size={12} color="var(--text-muted)" />}
          </button>
        </div>
        <StatusBadge status={delivery.status} size="sm" />
      </div>

      <div className="mobile-card-body">
        <div className="mobile-recipient-row">
          <div className="mobile-card-recipient">{delivery.recipientName}</div>
          <a
            href={`tel:${delivery.recipientPhone}`}
            className="mobile-call-pill"
            title="Call Recipient"
          >
            <PhoneIcon size={12} />
            <span>Call</span>
          </a>
        </div>
        <div className="mobile-address-row">
          <LocationPinIcon size={14} color="var(--text-muted)" className="mobile-address-pin" />
          <span className="mobile-card-address">{delivery.deliveryAddress}</span>
        </div>
      </div>

      <div className="mobile-card-footer">
        <span className="mobile-timestamp">
          {delivery.assignedAt}
        </span>
        <Link
          to={`/courier/deliveries/${delivery.trackingNumber}`}
          className={`btn-action ${!isCompleted ? 'primary' : 'secondary'} mobile-action-btn`}
        >
          {!isCompleted ? (
            <>
              <PencilIcon size={13} />
              <span>Update Status</span>
            </>
          ) : (
            <>
              <EyeIcon size={13} />
              <span>View Audit</span>
            </>
          )}
        </Link>
      </div>
    </article>
  );
};
