import { useEffect, useState, type FormEvent } from 'react';
import {
  addSupportReply, createSupportTicket, getSupportTicket, listSupportTickets,
  type CreateSupportTicket, type SupportCategory, type SupportTicket,
} from '../api/customerApi';
import s from '../components/CustomerPortal.module.css';

const categories: { value: SupportCategory; label: string }[] = [
  { value: 'DELIVERY_STATUS', label: 'Delivery tracking or status' },
  { value: 'DELAY', label: 'Delayed delivery' },
  { value: 'DAMAGED_PARCEL', label: 'Damaged or missing parcel' },
  { value: 'BILLING', label: 'Delivery charges or receipt' },
  { value: 'OTHER', label: 'Other question' },
];

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unable to complete your request.';
}

export function CustomerSupportPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [selected, setSelected] = useState<SupportTicket | null>(null);
  const [form, setForm] = useState<CreateSupportTicket>({
    category: 'DELIVERY_STATUS', subject: '', message: '',
    ...(Number(new URLSearchParams(window.location.search).get('delivery')) > 0
      ? { deliveryId: Number(new URLSearchParams(window.location.search).get('delivery')) } : {}),
  });
  const [reply, setReply] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    let active = true;
    listSupportTickets().then(items => { if (active) setTickets(items); })
      .catch(e => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function openTicket(id: number) {
    setError(''); setSuccess(''); setBusy(true);
    try { setSelected(await getSupportTicket(id)); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      const created = await createSupportTicket(form);
      setTickets(items => [created, ...items]);
      setSelected(created);
      setForm({ category: 'DELIVERY_STATUS', subject: '', message: '' });
      setSuccess(`Support request #${created.id} was submitted.`);
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  async function submitReply(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      const updated = await addSupportReply(selected.id, reply);
      setSelected(updated);
      setTickets(items => items.map(item => item.id === updated.id ? updated : item));
      setReply('');
      setSuccess('Your message was added to the support request.');
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  return <div className={s.stack}>
    <div className={s.headingRow}>
      <div><h2>Help & support</h2><p>Ask questions about a shipment and follow your support requests.</p></div>
      {selected && <button type="button" className={s.secondary} onClick={() => setSelected(null)}>New request</button>}
    </div>
    {error && <div role="alert" className={s.alert}>{error}</div>}
    {success && <div role="status" className={s.notice}>{success}</div>}
    <div className={s.twoColumns}>
      <section className={s.card}>
        <h3>My support requests</h3>
        {loading ? <p className={s.muted}>Loading requests…</p> : tickets.length === 0
          ? <p className={s.muted}>No requests yet. Submit a question using the form.</p>
          : <div className={s.supportList}>{tickets.map(ticket =>
            <button type="button" className={`${s.supportItem} ${selected?.id === ticket.id ? s.supportSelected : ''}`}
                    key={ticket.id} onClick={() => void openTicket(ticket.id)} disabled={busy}>
              <strong>#{ticket.id} · {ticket.subject}</strong>
              <span>{ticket.status.replace('_', ' ')} · {new Date(ticket.createdAt).toLocaleDateString('en-LK')}</span>
            </button>)}</div>}
      </section>
      {selected ? <section className={s.card}>
        <div className={s.cardHead}><h3>Request #{selected.id}</h3><strong>{selected.status.replace('_',' ')}</strong></div>
        <p><strong>{selected.subject}</strong></p>
        {selected.trackingNumber && <p className={s.muted}>Shipment: {selected.trackingNumber}</p>}
        <div className={s.supportMessages}>{selected.messages.map((item, i) =>
          <article key={item.id ?? i} className={s.supportMessage}>
            <div className={s.cardHead}><strong>{item.senderRole === 'CUSTOMER' ? 'You' : 'Support team'}</strong>
              <small>{item.createdAt ? new Date(item.createdAt).toLocaleString('en-LK') : 'Just now'}</small></div>
            <p>{item.message}</p>
          </article>)}</div>
        {selected.status === 'RESOLVED' ? <p className={s.muted}>This request is closed. Create another request if you need help.</p>
          : <form onSubmit={e => void submitReply(e)} className={s.stack}>
            <label className={s.wide}><span>Add a message</span>
              <textarea required rows={4} minLength={2} maxLength={2000} value={reply}
                        onChange={e => setReply(e.target.value)} /></label>
            <div className={s.alignRight}><button type="submit" className={s.primary} disabled={busy}>
              {busy ? 'Sending…' : 'Send message'}</button></div>
          </form>}
      </section> : <section className={s.card}>
        <h3>Open a support request</h3>
        <p className={s.muted}>Include your tracking number or associate an existing booking when applicable.</p>
        <form onSubmit={e => void submit(e)} className={s.stack}>
          <label className={s.field}><span>Topic</span>
            <select value={form.category} onChange={e => setForm(old => ({ ...old, category: e.target.value as SupportCategory }))}>
              {categories.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</select></label>
          <label className={s.field}><span>Subject</span>
            <input required maxLength={120} minLength={3} value={form.subject}
              onChange={e => setForm(old => ({ ...old, subject: e.target.value }))} placeholder="Brief description of the issue" /></label>
          <label className={s.field}><span>My delivery ID (optional)</span>
            <input type="number" min="1" step="1" value={form.deliveryId ?? ''}
              onChange={e => setForm(old => ({ ...old, deliveryId: e.target.value ? Number(e.target.value) : undefined }))}
              placeholder="Booking ID from My Deliveries" /></label>
          <label className={s.wide}><span>Your message</span>
            <textarea required rows={6} maxLength={2000} minLength={5} value={form.message}
              onChange={e => setForm(old => ({ ...old, message: e.target.value }))}
              placeholder="Tell us what happened and how we can help" /></label>
          <div className={s.alignRight}><button type="submit" disabled={busy} className={s.primary}>
            {busy ? 'Submitting…' : 'Submit support request'}</button></div>
        </form>
      </section>}
    </div>
    <p className={s.smallNote}>Customer requests are stored privately in SwiftDrop. Replies from staff require the team's support administration integration.</p>
  </div>;
}
