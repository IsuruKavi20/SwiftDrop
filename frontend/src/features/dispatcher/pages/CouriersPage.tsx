import { useState } from 'react';
import {
  createCourierProfile, findUserByEmail, listCourierAccountsWithoutProfile, listCourierProfiles, updateCourierProfile,
} from '../api/adminApi';
import type { AdminUser, CourierProfile, CourierProfileInput } from '../api/adminTypes';
import type { CourierAvailability } from '../api/dispatcherTypes';
import { AvailabilityBadge, ToneBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { Dropdown } from '../components/Dropdown';
import { FormDialog } from '../components/FormDialog';
import { availabilityLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

const AVAILABILITIES = Object.keys(availabilityLabels) as CourierAvailability[];
const withEmail = (name: string, email: string) => (email ? `${name} · ${email}` : name);

/** Add (courier undefined) or edit a courier's work profile: the courier table's phone, vehicle and availability. */
function CourierDialog({ courier, onClose, onSaved }: { courier?: CourierProfile; onClose: () => void; onSaved: (message: string) => void }) {
  const { data: listed } = useDispatchData(listCourierAccountsWithoutProfile, []);
  const [userId, setUserId] = useState('');
  // A Courier account the backend hasn't seen yet, found in the auth service by email
  const [found, setFound] = useState<AdminUser>();
  const [email, setEmail] = useState('');
  const [lookup, setLookup] = useState<{ busy: boolean; message?: string }>({ busy: false });
  const accounts = found && !listed?.some((a) => a.id === found.id) ? [...(listed ?? []), found] : listed;

  async function lookUp() {
    setLookup({ busy: true });
    try {
      const person = await findUserByEmail(email);
      if (!person) setLookup({ busy: false, message: `No SwiftDrop account uses ${email.trim()}.` });
      else if (!person.roles.includes('COURIER')) setLookup({ busy: false, message: `${person.name} doesn't have the Courier role. Give it in Users and Roles first.` });
      else if (!person.active) setLookup({ busy: false, message: `${person.name}'s account is blocked.` });
      else {
        setFound(person);
        setUserId(person.id);
        setLookup({ busy: false });
      }
    } catch (failure) {
      setLookup({ busy: false, message: failure instanceof Error ? failure.message : 'The lookup failed. Please try again.' });
    }
  }
  const [form, setForm] = useState<CourierProfileInput>({
    phoneNumber: courier?.phoneNumber ?? '',
    vehicleType: courier?.vehicleType ?? '',
    vehicleNumber: courier?.vehicleNumber ?? '',
    availability: courier?.availability ?? 'OFFLINE',
  });
  const set = (field: keyof CourierProfileInput) => (value: string) => setForm((f) => ({ ...f, [field]: value }));
  const adding = !courier;

  return (
    <FormDialog title={adding ? 'Add courier' : 'Edit courier'} subtitle={courier ? withEmail(courier.name, courier.email) : 'Create the work profile for a Courier account.'}
      submitLabel={adding ? 'Add courier' : 'Save'} canSubmit={(!adding || userId !== '') && form.phoneNumber.trim() !== ''} onClose={onClose}
      onSubmit={async () => {
        if (courier) {
          await updateCourierProfile(courier.courierId, form);
          onSaved(`${courier.name}'s profile is saved.`);
        } else {
          await createCourierProfile(userId, form);
          onSaved(`${accounts?.find((a) => a.id === userId)?.name ?? 'The courier'} can now be assigned deliveries when Available.`);
        }
      }}>
      <div className={styles.formGrid}>
        {adding && (
          <label className={`${styles.field} ${styles.wide}`}>
            <span>Courier account</span>
            <Dropdown value={userId} onChange={setUserId} placeholder="Choose an account…"
              emptyMessage={listed ? 'Every Courier account already has a profile. Find another one by email below.' : 'Loading accounts…'}
              options={(accounts ?? []).map((a) => ({ value: a.id, label: withEmail(a.name, a.email) }))} />
          </label>
        )}
        {adding && (
          <div className={`${styles.field} ${styles.wide}`}>
            <label htmlFor="courier-email">Not in the list? Find a Courier account by email</label>
            <span className={styles.lookupRow}>
              <input id="courier-email" type="email" value={email} placeholder="courier@example.com"
                onChange={(e) => { setEmail(e.target.value); setLookup({ busy: false }); }} />
              <button className={styles.secondaryButton} type="button" disabled={lookup.busy || !email.includes('@')}
                onClick={() => void lookUp()}>
                {lookup.busy ? 'Looking up…' : 'Find'}
              </button>
            </span>
            {lookup.message && <small role="alert">{lookup.message}</small>}
          </div>
        )}
        <label className={styles.field}>
          <span>Phone number</span>
          <input type="tel" value={form.phoneNumber} onChange={(e) => set('phoneNumber')(e.target.value)} maxLength={20} required placeholder="0771234567" />
        </label>
        <label className={styles.field}>
          <span>Availability</span>
          <Dropdown value={form.availability} onChange={set('availability')}
            options={AVAILABILITIES.map((a) => ({ value: a, label: availabilityLabels[a] }))} />
        </label>
        <label className={styles.field}>
          <span>Vehicle type</span>
          <input value={form.vehicleType} onChange={(e) => set('vehicleType')(e.target.value)} maxLength={30} placeholder="Motorbike" />
        </label>
        <label className={styles.field}>
          <span>Vehicle number</span>
          <input value={form.vehicleNumber} onChange={(e) => set('vehicleNumber')(e.target.value)} maxLength={30} placeholder="WP BCD-4521" />
        </label>
      </div>
    </FormDialog>
  );
}

export function CouriersPage() {
  const { data, error, loading } = useDispatchData(listCourierProfiles, []);
  const [editing, setEditing] = useState<CourierProfile | 'new'>();
  const { showToast, toast } = useToast();

  return (
    <>
      <PageHeader title="Couriers" subtitle="Work profiles of people with the Courier role. Block an account in Users and Roles."
        action={(
          <button className={styles.primaryButton} type="button" onClick={() => setEditing('new')}>
            <DispatcherIcon name="plus" size={18} /> Add courier
          </button>
        )} />
      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Courier</th>
                <th scope="col">Account</th>
                <th scope="col">Vehicle</th>
                <th scope="col">Availability</th>
                <th scope="col" className={styles.numeric}>Active deliveries</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.map((c) => (
                <tr key={c.courierId}>
                  <td>
                    <strong>{c.name}</strong>
                    {(!c.accountActive || !c.courierRole) && (
                      <span className={styles.badgeRow}>
                        {!c.accountActive && <ToneBadge tone="grey">Account blocked</ToneBadge>}
                        {!c.courierRole && <ToneBadge tone="amber">No Courier role</ToneBadge>}
                      </span>
                    )}
                  </td>
                  <td>{c.email || <span className={styles.muted}>Email unavailable</span>}<a className={styles.cellSub} href={`tel:${c.phoneNumber}`}>{c.phoneNumber}</a></td>
                  <td>
                    {c.vehicleType ?? <span className={styles.muted}>—</span>}
                    {c.vehicleNumber && <small className={styles.cellSub}>{c.vehicleNumber}</small>}
                  </td>
                  <td><AvailabilityBadge availability={c.availability} /></td>
                  <td className={`${styles.numeric} ${styles.strongNumber}`}>{c.activeDeliveries}</td>
                  <td className={styles.actionCell}>
                    <button className={styles.secondaryButton} type="button" onClick={() => setEditing(c)}>
                      <DispatcherIcon name="edit" size={16} /> Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data?.length === 0 && <p className={styles.empty}>No courier profiles yet.</p>}
      </div>

      {editing && (
        <CourierDialog courier={editing === 'new' ? undefined : editing} onClose={() => setEditing(undefined)}
          onSaved={(message) => { setEditing(undefined); showToast(message); }} />
      )}
      {toast}
    </>
  );
}
