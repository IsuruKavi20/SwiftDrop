import { useState } from 'react';
import { createCourierProfile, listCourierAccountsWithoutProfile, listCourierProfiles, updateCourierProfile } from '../api/adminApi';
import type { CourierProfile, CourierProfileInput } from '../api/adminTypes';
import type { CourierAvailability } from '../api/dispatcherTypes';
import { AvailabilityBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { FormDialog } from '../components/FormDialog';
import { availabilityLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

const AVAILABILITIES = Object.keys(availabilityLabels) as CourierAvailability[];

/** Add (courier undefined) or edit a courier's work profile: the courier table's phone, vehicle and availability. */
function CourierDialog({ courier, onClose, onSaved }: { courier?: CourierProfile; onClose: () => void; onSaved: (message: string) => void }) {
  const { data: accounts } = useDispatchData(listCourierAccountsWithoutProfile, []);
  const [userId, setUserId] = useState('');
  const [form, setForm] = useState<CourierProfileInput>({
    phoneNumber: courier?.phoneNumber ?? '',
    vehicleType: courier?.vehicleType ?? '',
    vehicleNumber: courier?.vehicleNumber ?? '',
    availability: courier?.availability ?? 'OFFLINE',
  });
  const set = (field: keyof CourierProfileInput) => (value: string) => setForm((f) => ({ ...f, [field]: value }));
  const adding = !courier;

  return (
    <FormDialog title={adding ? 'Add courier' : 'Edit courier'} subtitle={courier ? `${courier.name} · ${courier.email}` : 'Create the work profile for a Courier account.'}
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
            <select value={userId} onChange={(e) => setUserId(e.target.value)} required>
              <option value="">Choose an account…</option>
              {accounts?.map((a) => <option key={a.id} value={a.id}>{a.name} · {a.email}</option>)}
            </select>
          </label>
        )}
        {adding && accounts?.length === 0 && (
          <p className={styles.infoBox}>Every Courier account already has a profile. Give someone the Courier role in Users and Roles first.</p>
        )}
        <label className={styles.field}>
          <span>Phone number</span>
          <input type="tel" value={form.phoneNumber} onChange={(e) => set('phoneNumber')(e.target.value)} maxLength={20} required placeholder="0771234567" />
        </label>
        <label className={styles.field}>
          <span>Availability</span>
          <select value={form.availability} onChange={(e) => set('availability')(e.target.value)}>
            {AVAILABILITIES.map((a) => <option key={a} value={a}>{availabilityLabels[a]}</option>)}
          </select>
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
                  <td><strong>{c.name}</strong></td>
                  <td>{c.email}<a className={styles.cellSub} href={`tel:${c.phoneNumber}`}>{c.phoneNumber}</a></td>
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
