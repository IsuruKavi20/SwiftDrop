import { useState } from 'react';
import { listUsers, setUserBlocked, setUserRoles } from '../api/adminApi';
import type { AdminRole, AdminUser } from '../api/adminTypes';
import { ToneBadge, type Tone } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { FormDialog, ReasonField } from '../components/FormDialog';
import { SearchBox } from '../components/SearchBox';
import { roleLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast } from '../lib/useToast';
import styles from '../components/Dispatcher.module.css';

const ROLES = Object.keys(roleLabels) as AdminRole[];
const roleTones: Record<AdminRole, Tone> = { CUSTOMER: 'blue', MERCHANT: 'amber', COURIER: 'green', ADMIN: 'red' };
const roleHints: Record<AdminRole, string> = {
  CUSTOMER: 'Books and tracks own deliveries',
  MERCHANT: 'Business orders, API keys, webhooks',
  COURIER: 'Carries assigned deliveries',
  ADMIN: 'Everything, including dispatch',
};

function RolesDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: (message: string) => void }) {
  const [roles, setRoles] = useState<AdminRole[]>(user.roles);
  const toggle = (role: AdminRole) => setRoles((current) => (current.includes(role) ? current.filter((r) => r !== role) : [...current, role]));

  return (
    <FormDialog title="Edit roles" subtitle={`${user.name} · ${user.email}`} submitLabel="Save roles"
      canSubmit={roles.length > 0} onClose={onClose}
      onSubmit={async () => {
        await setUserRoles(user.id, roles);
        onSaved(`${user.name}'s roles are saved. They apply at the person's next sign-in refresh (within 15 minutes).`);
      }}>
      <fieldset className={styles.courierChoices}>
        <legend>Roles</legend>
        {ROLES.map((role) => (
          <label key={role} className={styles.courierChoice}>
            <input type="checkbox" checked={roles.includes(role)} onChange={() => toggle(role)} />
            <span><strong>{roleLabels[role]}</strong><small>{roleHints[role]}</small></span>
            <span />
          </label>
        ))}
      </fieldset>
      {roles.length === 0 && <p className={styles.muted}>Keep at least one role.</p>}
    </FormDialog>
  );
}

function BlockDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: (message: string) => void }) {
  const blocking = user.active;
  const [reason, setReason] = useState('');
  return (
    <FormDialog title={blocking ? 'Block account' : 'Unblock account'} subtitle={`${user.name} · ${user.email}`}
      submitLabel={blocking ? 'Block' : 'Unblock'} danger={blocking} canSubmit={reason.trim().length > 0} onClose={onClose}
      onSubmit={async () => {
        await setUserBlocked(user.id, blocking, reason);
        onSaved(blocking ? `${user.name} is blocked and signed out.` : `${user.name} can sign in again.`);
      }}>
      <p className={styles.infoBox}>
        {blocking
          ? 'They are signed out everywhere and cannot sign in until an admin unblocks them.'
          : 'They will be able to sign in again with their existing roles.'}
      </p>
      <ReasonField label="Reason (kept in the audit log)" value={reason} onChange={setReason}
        placeholder={blocking ? 'e.g. Repeated fake bookings' : 'e.g. Identity confirmed by phone'} />
    </FormDialog>
  );
}

export function UsersRolesPage() {
  const [text, setText] = useState('');
  const [role, setRole] = useState<AdminRole | ''>('');
  const [editing, setEditing] = useState<AdminUser>();
  const [blocking, setBlocking] = useState<AdminUser>();
  const { showToast, toast } = useToast();
  const { data, error, loading } = useDispatchData(listUsers, []);

  const needle = text.trim().toLowerCase();
  const rows = data?.filter((u) => (!role || u.roles.includes(role))
    && (!needle || u.name.toLowerCase().includes(needle) || u.email.toLowerCase().includes(needle)));

  const done = (message: string) => {
    setEditing(undefined);
    setBlocking(undefined);
    showToast(message);
  };

  return (
    <>
      <PageHeader title="Users and Roles" subtitle={data ? `${data.length} accounts` : 'Accounts in the SwiftDrop auth service'} />

      <div className={styles.toolbar}>
        <SearchBox value={text} onChange={setText} label="Search accounts" placeholder="Search by name or email" />
        <label className={styles.select}>
          <span className={styles.visuallyHidden}>Role</span>
          <select value={role} onChange={(e) => setRole(e.target.value as AdminRole | '')}>
            <option value="">All roles</option>
            {ROLES.map((r) => <option key={r} value={r}>{roleLabels[r]}</option>)}
          </select>
        </label>
      </div>

      {error && <p className={styles.errorBox} role="alert">{error}</p>}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Roles</th>
                <th scope="col">State</th>
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {rows?.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.name}</strong></td>
                  <td>{u.email}</td>
                  <td>
                    <span className={styles.badgeRow}>
                      {u.roles.map((r) => <ToneBadge key={r} tone={roleTones[r]}>{roleLabels[r]}</ToneBadge>)}
                    </span>
                  </td>
                  <td>{u.active ? <ToneBadge tone="green">Active</ToneBadge> : <ToneBadge tone="grey">Blocked</ToneBadge>}</td>
                  <td className={styles.actionCell}>
                    <span className={styles.rowActions}>
                      <button className={styles.secondaryButton} type="button" onClick={() => setEditing(u)}>
                        <DispatcherIcon name="edit" size={16} /> Edit roles
                      </button>
                      {u.active ? (
                        <button className={styles.dangerButton} type="button" onClick={() => setBlocking(u)}>
                          <DispatcherIcon name="lock" size={16} /> Block
                        </button>
                      ) : (
                        <button className={styles.secondaryButton} type="button" onClick={() => setBlocking(u)}>
                          <DispatcherIcon name="unlock" size={16} /> Unblock
                        </button>
                      )}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {rows?.length === 0 && <p className={styles.empty}>No accounts match.</p>}
      </div>

      {editing && <RolesDialog user={editing} onClose={() => setEditing(undefined)} onSaved={done} />}
      {blocking && <BlockDialog user={blocking} onClose={() => setBlocking(undefined)} onSaved={done} />}
      {toast}
    </>
  );
}
