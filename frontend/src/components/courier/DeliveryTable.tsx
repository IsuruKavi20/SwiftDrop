import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import type { DeliverySummary } from '../../types/courier';
import { StatusBadge } from '../common/Badge';
import { LocationPinIcon, EyeIcon, PencilIcon, CopyIcon, CheckIcon, ChevronRightIcon } from '../common/Icons';

interface DeliveryTableProps {
  deliveries: DeliverySummary[];
}

export const DeliveryTable: React.FC<DeliveryTableProps> = ({ deliveries }) => {
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const handleCopy = (e: React.MouseEvent, tracking: string) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(tracking);
    setCopiedId(tracking);
    setTimeout(() => {
      setCopiedId((curr) => (curr === tracking ? null : curr));
    }, 1800);
  };

  if (deliveries.length === 0) {
    return (
      <div className="table-empty-state">
        <p className="table-empty-title">No deliveries found</p>
        <p className="table-empty-subtitle">Try adjusting your status filter or search keywords.</p>
      </div>
    );
  }

  return (
    <div className="table-responsive">
      <table className="deliveries-table">
        <thead>
          <tr>
            <th>Tracking Code</th>
            <th>Recipient</th>
            <th>Destination Address</th>
            <th>Status</th>
            <th style={{ textAlign: 'right' }}>Operations</th>
          </tr>
        </thead>
        <tbody>
          {deliveries.map((delivery) => {
            const isCompleted = ['DELIVERED', 'FAILED', 'CANCELLED'].includes(delivery.status);
            const isCopied = copiedId === delivery.trackingNumber;

            return (
              <tr key={delivery.id} className="delivery-row">
                <td className="tracking-col">
                  <div className="tracking-badge-group">
                    <Link
                      to={`/courier/deliveries/${delivery.trackingNumber}`}
                      className="tracking-number-code"
                      title="View Delivery Details"
                    >
                      {delivery.trackingNumber}
                    </Link>
                    <button
                      type="button"
                      className={`copy-btn-mini ${isCopied ? 'copied' : ''}`}
                      title={isCopied ? 'Copied to clipboard!' : 'Copy tracking number'}
                      onClick={(e) => handleCopy(e, delivery.trackingNumber)}
                    >
                      {isCopied ? (
                        <CheckIcon size={12} color="#10B981" />
                      ) : (
                        <CopyIcon size={12} color="var(--text-muted)" />
                      )}
                    </button>
                  </div>
                </td>
                <td>
                  <div className="recipient-col">
                    <span className="recipient-name">{delivery.recipientName}</span>
                    <a
                      href={`tel:${delivery.recipientPhone}`}
                      className="recipient-phone-link"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {delivery.recipientPhone}
                    </a>
                  </div>
                </td>
                <td>
                  <div className="address-wrapper">
                    <LocationPinIcon size={14} color="var(--text-muted)" className="address-pin-icon" />
                    <span className="address-text" title={delivery.deliveryAddress}>
                      {delivery.deliveryAddress}
                    </span>
                  </div>
                </td>
                <td>
                  <StatusBadge status={delivery.status} size="sm" />
                </td>
                <td style={{ textAlign: 'right' }}>
                  <Link
                    to={`/courier/deliveries/${delivery.trackingNumber}`}
                    className={`btn-action ${!isCompleted ? 'primary' : 'secondary'}`}
                  >
                    {!isCompleted ? (
                      <>
                        <PencilIcon size={13} />
                        <span>Update Status</span>
                        <ChevronRightIcon size={12} />
                      </>
                    ) : (
                      <>
                        <EyeIcon size={13} />
                        <span>View Audit</span>
                      </>
                    )}
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
