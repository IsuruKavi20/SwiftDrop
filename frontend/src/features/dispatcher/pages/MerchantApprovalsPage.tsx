import { useState } from 'react';
import { decideMerchant, listMerchantApplications, setMerchantSuspended } from '../api/adminApi';
import type { MerchantApplication, MerchantApplicationStatus } from '../api/adminTypes';
import { ToneBadge, type Tone } from '../components/Badges';
import { PageHeader } from '../components/DispatcherLayout';
import { FormDialog, ReasonField } from '../components/FormDialog';
import { formatDateTime, merchantStatusLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

const statusTones: Record<MerchantApplicationStatus, Tone> = { PENDING: 'amber', APPROVED: 'green', REJECTED: 'red', SUSPENDED: 'grey' };

function MerchantStatus({ status }: { status: MerchantApplicationStatus }) {
  return <ToneBadge tone={statusTones[status]}>{merchantStatusLabels[status]}</ToneBadge>;
}

type Pending = { application: MerchantApplication; action: 'APPROVE' | 'REJECT' | 'SUSPEND' | 'REINSTATE' };

function DecisionDialog({ pending, onClose, onSaved }: { pending: Pending; onClose: () => void; onSaved: (message: string) => void }) {
  const { application: m, action } = pending;
  const [reason, setReason] = useState('');
  const copy = {
    APPROVE: { title: 'Approve merchant', button: 'Approve', danger: false, info: 'They get the Merchant role: business orders, API keys and webhooks.', done: `${m.businessName} is approved.` },
    REJECT: { title: 'Reject application', button: 'Reject', danger: true, info: 'The applicant sees your reason and can apply again after 7 days.', done: `${m.businessName}'s application is rejected.` },
    SUSPEND: { title: 'Suspend merchant', button: 'Suspend', danger: true, info: 'Their API keys and merchant pages stop working until you reinstate them.', done: `${m.businessName} is suspended.` },
    REINSTATE: { title: 'Reinstate merchant', button: 'Reinstate', danger: false, info: 'Their merchant access works again.', done: `${m.businessName} is reinstated.` },
  }[action];

  return (
    <FormDialog title={copy.title} subtitle={`${m.businessName} · ${m.registrationNumber}`} submitLabel={copy.button} danger={copy.danger}
      canSubmit={action !== 'REJECT' || reason.trim().length > 0} onClose={onClose}
      onSubmit={async () => {
        if (action === 'APPROVE' || action === 'REJECT') await decideMerchant(m.merchantId, action, reason);
        else await setMerchantSuspended(m.merchantId, action === 'SUSPEND');
        onSaved(copy.done);
      }}>
      <p className={styles.infoBox}>{copy.info}</p>
      {action === 'REJECT' && (
        <ReasonField label="Reason shown to the applicant" value={reason} onChange={setReason}
          placeholder="e.g. The registration number does not match the business name" />
      )}
    </FormDialog>
  );
}

export function MerchantApprovalsPage() {
  const { data, error, loading } = useDispatchData(() => listMerchantApplications(), []);
  const [pending, setPending] = useState<Pending>();
  const { showToast, toast } = useToast();

  const waiting = data?.filter((m) => m.status === 'PENDING') ?? [];
  const reviewed = data?.filter((m) => m.status !== 'PENDING') ?? [];

  return (
    <>
      <PageHeader title="Merchant Approvals" subtitle="Approving a merchant unlocks business orders, API keys and webhooks." />
      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <h2 className={styles.sectionTitle}>Waiting for a decision ({waiting.length})</h2>
      {data && waiting.length === 0 && <p className={styles.infoBox}>No applications are waiting.</p>}
      <div className={styles.cardGrid} aria-busy={loading}>
        {waiting.map((m) => (
          <article key={m.merchantId} className={styles.appCard}>
            <header>
              <div>
                <h3>{m.businessName}</h3>
                <small>Applied {formatDateTime(m.submittedAt)}</small>
              </div>
              <MerchantStatus status={m.status} />
            </header>
            <dl>
              <dt>Registration no.</dt><dd className={styles.mono}>{m.registrationNumber}</dd>
              <dt>Website</dt>
              <dd>{m.website ? <a href={m.website} target="_blank" rel="noreferrer">{m.website}</a> : <span className={styles.muted}>Not given</span>}</dd>
              <dt>Applicant</dt><dd>{m.applicantName}</dd>
              <dt>Email</dt><dd><a href={`mailto:${m.applicantEmail}`}>{m.applicantEmail}</a></dd>
            </dl>
            <div className={styles.cardActions}>
              <button className={styles.primaryButton} type="button" onClick={() => setPending({ application: m, action: 'APPROVE' })}>Approve</button>
              <button className={styles.dangerButton} type="button" onClick={() => setPending({ application: m, action: 'REJECT' })}>Reject</button>
            </div>
          </article>
        ))}
      </div>

      <h2 className={styles.sectionTitle}>Reviewed</h2>
      <div className={styles.tableCard}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Business</th>
                <th scope="col">Applicant</th>
                <th scope="col">Decided</th>
                <th scope="col">Status</th>
                <th scope="col">Note</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {reviewed.map((m) => (
                <tr key={m.merchantId}>
                  <td><strong>{m.businessName}</strong><small className={styles.cellSub}>{m.registrationNumber}</small></td>
                  <td>{m.applicantName}<small className={styles.cellSub}>{m.applicantEmail}</small></td>
                  <td className={styles.dateCell}>{m.decidedAt ? formatDateTime(m.decidedAt) : '—'}</td>
                  <td><MerchantStatus status={m.status} /></td>
                  <td className={styles.reasonCell}>{m.reason ?? <span className={styles.muted}>—</span>}</td>
                  <td className={styles.actionCell}>
                    {m.status === 'APPROVED' && (
                      <button className={styles.dangerButton} type="button" onClick={() => setPending({ application: m, action: 'SUSPEND' })}>Suspend</button>
                    )}
                    {m.status === 'SUSPENDED' && (
                      <button className={styles.secondaryButton} type="button" onClick={() => setPending({ application: m, action: 'REINSTATE' })}>Reinstate</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && reviewed.length === 0 && <p className={styles.empty}>No decisions yet.</p>}
      </div>

      {pending && <DecisionDialog pending={pending} onClose={() => setPending(undefined)}
        onSaved={(message) => { setPending(undefined); showToast(message); }} />}
      {toast}
    </>
  );
}
