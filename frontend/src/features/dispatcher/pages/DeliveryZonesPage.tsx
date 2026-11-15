import { useState } from 'react';
import { districtsByProvince, listZones, saveZone } from '../api/adminApi';
import type { DeliveryZone, DeliveryZoneInput } from '../api/adminTypes';
import { ToneBadge } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { FormDialog } from '../components/FormDialog';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

function ZoneDialog({ zone, zones, onClose, onSaved }: { zone?: DeliveryZone; zones: DeliveryZone[]; onClose: () => void; onSaved: (message: string) => void }) {
  const [form, setForm] = useState<DeliveryZoneInput>({
    name: zone?.name ?? '', code: zone?.code ?? '', districts: zone?.districts ?? [], active: zone?.active ?? true,
  });
  /** District → the code of the other zone that already covers it. */
  const takenBy = new Map(zones.filter((z) => z.id !== zone?.id).flatMap((z) => z.districts.map((d) => [d, z.code] as const)));
  const toggle = (district: string) => setForm((f) => ({
    ...f, districts: f.districts.includes(district) ? f.districts.filter((d) => d !== district) : [...f.districts, district],
  }));

  return (
    <FormDialog wide title={zone ? 'Edit zone' : 'Add zone'} subtitle="A zone groups districts. Each district belongs to one zone at most."
      submitLabel={zone ? 'Save zone' : 'Add zone'} onClose={onClose}
      canSubmit={form.name.trim() !== '' && form.code.trim() !== '' && form.districts.length > 0}
      onSubmit={async () => {
        await saveZone(zone?.id ?? null, form);
        onSaved(`${form.name.trim()} is saved.`);
      }}>
      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span>Zone name</span>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={60} required placeholder="Western Metro" />
        </label>
        <label className={styles.field}>
          <span>Code</span>
          <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} maxLength={4} required placeholder="WP" />
        </label>
        <label className={`${styles.check} ${styles.wide}`}>
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span>Active: bookings to and from these districts are accepted</span>
        </label>
      </div>
      <div>
        {Object.entries(districtsByProvince).map(([province, districts]) => (
          <fieldset key={province} className={`${styles.checkGrid} ${styles.provinceGroup}`}>
            <legend>{province} Province</legend>
            {districts.map((d) => {
              const other = takenBy.get(d);
              return (
                <label key={d} className={styles.check}>
                  <input type="checkbox" checked={form.districts.includes(d)} disabled={other !== undefined} onChange={() => toggle(d)} />
                  <span>{d} {other && <small>({other})</small>}</span>
                </label>
              );
            })}
          </fieldset>
        ))}
      </div>
    </FormDialog>
  );
}

export function DeliveryZonesPage() {
  const { data, error, loading } = useDispatchData(listZones, []);
  const [editing, setEditing] = useState<DeliveryZone | 'new'>();
  const { showToast, toast } = useToast();
  const covered = new Set(data?.flatMap((z) => z.districts));
  const uncovered = Object.values(districtsByProvince).flat().filter((d) => !covered.has(d));

  return (
    <>
      <PageHeader title="Delivery Zones" subtitle="Districts grouped into operating zones."
        action={(
          <button className={styles.primaryButton} type="button" onClick={() => setEditing('new')}>
            <DispatcherIcon name="plus" size={18} /> Add zone
          </button>
        )} />
      <p className={styles.infoBox}>
        Prices don't depend on zones: the backend compares the pickup and destination districts (see Pricing).
      </p>
      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Zone</th>
                <th scope="col">Code</th>
                <th scope="col">Districts</th>
                <th scope="col">State</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.map((z) => (
                <tr key={z.id}>
                  <td><strong>{z.name}</strong></td>
                  <td className={styles.mono}>{z.code}</td>
                  <td>{z.districts.join(', ')}</td>
                  <td>{z.active ? <ToneBadge tone="green">Active</ToneBadge> : <ToneBadge tone="grey">Inactive</ToneBadge>}</td>
                  <td className={styles.actionCell}>
                    <button className={styles.secondaryButton} type="button" onClick={() => setEditing(z)}>
                      <DispatcherIcon name="edit" size={16} /> Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data?.length === 0 && <p className={styles.empty}>No zones yet.</p>}
      </div>
      {data && uncovered.length > 0 && (
        <p className={styles.panelNote} style={{ padding: '0.75rem 0 0' }}>Not in any zone: {uncovered.join(', ')}.</p>
      )}

      {editing && data && (
        <ZoneDialog zone={editing === 'new' ? undefined : editing} zones={data} onClose={() => setEditing(undefined)}
          onSaved={(message) => { setEditing(undefined); showToast(message); }} />
      )}
      {toast}
    </>
  );
}
