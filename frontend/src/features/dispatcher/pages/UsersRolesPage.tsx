import { useState } from 'react';
import { findUserByEmail, listUsers, setUserBlocked, setUserRoles } from '../api/adminApi';
import type { AdminRole, AdminUser } from '../api/adminTypes';
import { ToneBadge, type Tone } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { Dropdown } from '../components/Dropdown';
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

const whoLine = (user: AdminUser) => (user.email ? `${user.name} · ${user.email}` : user.name);
const looksLikeEmail = (text: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text.trim());

function RolesDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: (message: string) => void }) {
  const [roles, setRoles] = useState<AdminRole[]>(user.roles);
  const toggle = (role: AdminRole) => setRoles((current) => (current.includes(role) ? current.filter((r) => r !== role) : [...current, role]));

  return (
    <FormDialog title="Edit roles" subtitle={whoLine(user)} submitLabel="Save roles"
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
    <FormDialog title={blocking ? 'Block account' : 'Unblock account'} subtitle={whoLine(user)}
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
  // People found by email: accounts with no role listed by the access review (e.g. plain customers)
  const [found, setFound] = useState<AdminUser[]>([]);
  // The email each found person was looked up by: the auth service may answer without the email (or with a name that
  // doesn't contain it), and the search box must not then hide the person the admin just found
  const [searchedAs, setSearchedAs] = useState<Record<string, string>>({});
  const [lookup, setLookup] = useState<{ busy: boolean; message?: string }>({ busy: false });
  // Re-reads found people after a role change or block (the access review doesn't list people with no role)
  const { data: refreshedFound } = useDispatchData(
    () => Promise.all(found.map((u) => findUserByEmail(searchedAs[u.id] ?? u.email).then((fresh) => fresh ?? u))),
    [found.map((u) => u.id).join()],
  );
  // The newest copy of each found person; one found moments ago shows at once, before its re-read arrives
  const foundNow = found.map((u) => refreshedFound?.find((fresh) => fresh.id === u.id) ?? u);

  const known = new Set(data?.map((u) => u.id));
  const all = data ? [...data, ...foundNow.filter((u) => !known.has(u.id))] : undefined;
  const needle = text.trim().toLowerCase();
  const rows = all?.filter((u) => (!role || u.roles.includes(role))
    && (!needle || u.name.toLowerCase().includes(needle) || u.email.toLowerCase().includes(needle)
      || searchedAs[u.id] === needle));
  const canLookUp = looksLikeEmail(text) && rows?.length === 0;

  async function lookUp() {
    setLookup({ busy: true });
    try {
      const person = await findUserByEmail(text);
      if (person) {
        setSearchedAs((current) => ({ ...current, [person.id]: text.trim().toLowerCase() }));
        setFound((current) => [...current.filter((u) => u.id !== person.id), person]);
        setLookup({ busy: false });
      } else {
        setLookup({ busy: false, message: `No SwiftDrop account uses ${text.trim()}.` });
      }
    } catch (failure) {
      setLookup({ busy: false, message: failure instanceof Error ? failure.message : 'The lookup failed. Please try again.' });
    }
  }

  const done = (message: string) => {
    setEditing(undefined);
    setBlocking(undefined);
    showToast(message);
  };

  return (
    <>
      <PageHeader title="Users and Roles"
        subtitle={data ? `${data.length} accounts with a role. Type a full email to find anyone else.` : 'Accounts in the SwiftDrop auth service'} />

      <div className={styles.toolbar}>
        <SearchBox value={text} onChange={(value) => { setText(value); setLookup({ busy: false }); }} label="Search accounts"
          placeholder="Search by name or email" />
        <label className={styles.select}>
          <span className={styles.visuallyHidden}>Role</span>
          <Dropdown value={role} onChange={(v) => setRole(v as AdminRole | '')}
            options={[{ value: '', label: 'All roles' }, ...ROLES.map((r) => ({ value: r, label: roleLabels[r] }))]} />
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
                  <td>{u.email || searchedAs[u.id] || <span className={styles.muted}>—</span>}</td>
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
        {rows?.length === 0 && (
          <div className={styles.empty}>
            {lookup.message ?? (canLookUp ? 'Nobody with a role matches this email.' : 'No accounts match. Type a full email to look someone up.')}
            {canLookUp && !lookup.message && (
              <p>
                <button className={styles.secondaryButton} type="button" onClick={() => void lookUp()} disabled={lookup.busy}>
                  <DispatcherIcon name="search" size={16} /> {lookup.busy ? 'Looking up…' : `Find ${text.trim()}`}
                </button>
              </p>
            )}
          </div>
        )}
      </div>

      {editing && <RolesDialog user={editing} onClose={() => setEditing(undefined)} onSaved={done} />}
      {blocking && <BlockDialog user={blocking} onClose={() => setBlocking(undefined)} onSaved={done} />}
      {toast}
    </>
  );
}
