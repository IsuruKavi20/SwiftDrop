import { useState } from 'react';
import { findUserByEmail, listUsers, setUserBlocked, setUserRoles } from '../api/adminApi';
import type { AdminRole, AdminUser } from '../api/adminTypes';
import { ToneBadge, type Tone } from '../components/Badges';
import { DispatcherIcon } from '../components/DispatcherIcon';
import { PageHeader } from '../components/DispatcherLayout';
import { Dropdown } from '../components/Dropdown';
import { FormDialog, ReasonField } from '../components/FormDialog';
import { SearchBox } from '../components/SearchBox';
import { currentUserId } from '../lib/currentUser';
import { roleLabels } from '../lib/format';
import { useDispatchData } from '../lib/useDispatchData';
import { useToast, type ToastOptions } from '../lib/useToast';
import { LoadError } from '../components/States';
import { useUrlState } from '../lib/useUrlState';
import { SortHeader } from '../components/SortHeader';
import { useSort } from '../lib/useSort';
import { PersonName } from '../components/PersonName';
import { personName } from '../lib/names';
import { Email } from '../components/Email';
import { useFlash } from '../lib/useFlash';
import styles from '../components/Dispatcher.module.css';

const ROLES = Object.keys(roleLabels) as AdminRole[];
const roleTones: Record<AdminRole, Tone> = { CUSTOMER: 'blue', MERCHANT: 'amber', COURIER: 'green', ADMIN: 'red' };
const roleHints: Record<AdminRole, string> = {
  CUSTOMER: 'Books and tracks own deliveries',
  MERCHANT: 'Business orders, API keys, webhooks',
  COURIER: 'Carries assigned deliveries',
  ADMIN: 'Everything, including dispatch',
};

/** "Name · email" for dialog subtitles; accounts without a name get one made from their email. */
const nameOf = (user: AdminUser) => personName(user.name, user.email).display;
const whoLine = (user: AdminUser) => (user.email ? `${nameOf(user)} · ${user.email}` : nameOf(user));
const looksLikeEmail = (text: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text.trim());

/** What each removed role takes away, shown before saving. */
const removalWarnings: Record<AdminRole, string> = {
  ADMIN: 'They lose the admin area: users, merchants, couriers, pricing and dispatch.',
  COURIER: 'They can no longer be assigned deliveries. Their courier profile is kept.',
  MERCHANT: 'Their business orders, API keys and webhooks stop working.',
  CUSTOMER: 'They can no longer book or track their own deliveries.',
};

interface RolesDialogProps {
  user: AdminUser;
  /** The signed-in admin's id, to stop them removing their own Admin role. */
  selfId?: string;
  /** How many accounts hold Admin, so the last one can't lose it. */
  adminCount: number;
  onClose: () => void;
  onSaved: (message: string, options?: ToastOptions, changed?: string) => void;
}

function RolesDialog({ user, selfId, adminCount, onClose, onSaved }: Readonly<RolesDialogProps>) {
  const [roles, setRoles] = useState<AdminRole[]>(user.roles);
  const toggle = (role: AdminRole) => setRoles((current) => (current.includes(role) ? current.filter((r) => r !== role) : [...current, role]));

  const added = ROLES.filter((r) => roles.includes(r) && !user.roles.includes(r));
  const removed = ROLES.filter((r) => !roles.includes(r) && user.roles.includes(r));
  const removingAdmin = removed.includes('ADMIN');
  // The auth service refuses both; say so before the admin presses Save
  const blockedReason = (): string | undefined => {
    if (!removingAdmin) return undefined;
    if (user.id === selfId) return `You can't remove your own Admin role. Ask another admin to do it.`;
    if (adminCount <= 1) return `${nameOf(user)} is the only admin. Give Admin to someone else first, so SwiftDrop always has one.`;
    return undefined;
  };
  const blocked = blockedReason();
  const changed = added.length + removed.length;

  return (
    <FormDialog title="Edit roles" subtitle={whoLine(user)}
      submitLabel={changed ? `Save ${changed} ${changed === 1 ? 'change' : 'changes'}` : 'Save roles'}
      danger={removed.length > 0} canSubmit={roles.length > 0 && changed > 0 && !blocked} onClose={onClose}
      onSubmit={async () => {
        await setUserRoles(user.id, roles);
        const before = user.roles;
        onSaved(`${nameOf(user)}'s roles are saved. They apply at their next sign-in refresh (within 15 minutes).`,
          { undo: () => setUserRoles(user.id, before) }, user.id);
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

      {changed > 0 && (
        <div className={styles.changeSummary} aria-live="polite">
          <strong>What changes</strong>
          <ul className={styles.badgeRow}>
            {added.map((r) => <li key={r}><ToneBadge tone="green">+ {roleLabels[r]}</ToneBadge></li>)}
            {removed.map((r) => <li key={r}><ToneBadge tone="red">− {roleLabels[r]}</ToneBadge></li>)}
          </ul>
          {removed.length > 0 && (
            <ul className={styles.warnList}>
              {removed.map((r) => <li key={r}><strong>{roleLabels[r]}:</strong> {removalWarnings[r]}</li>)}
            </ul>
          )}
        </div>
      )}
      {blocked && <p className={styles.errorBox} role="alert">{blocked}</p>}
    </FormDialog>
  );
}

function BlockDialog({ user, selfId, onClose, onSaved }: Readonly<{ user: AdminUser; selfId?: string; onClose: () => void; onSaved: (message: string, options?: ToastOptions, changed?: string) => void }>) {
  const blocking = user.active;
  const self = blocking && user.id === selfId;
  const [reason, setReason] = useState('');
  return (
    <FormDialog title={blocking ? 'Block account' : 'Unblock account'} subtitle={whoLine(user)}
      submitLabel={blocking ? 'Block' : 'Unblock'} danger={blocking} canSubmit={reason.trim().length > 0 && !self} onClose={onClose}
      onSubmit={async () => {
        await setUserBlocked(user.id, blocking, reason);
        onSaved(blocking ? `${nameOf(user)} is blocked and signed out.` : `${nameOf(user)} can sign in again.`, undefined, user.id);
      }}>
      {self ? (
        <p className={styles.errorBox} role="alert">This is your own account. You can't block yourself; ask another admin.</p>
      ) : (
        <p className={blocking ? styles.warnBox : styles.infoBox}>
          {blocking
            ? `${nameOf(user)} is signed out everywhere straight away and can't sign in until an admin unblocks them.${user.roles.includes('COURIER') ? ' Reassign their active deliveries first.' : ''}`
            : 'They will be able to sign in again with their existing roles.'}
        </p>
      )}
      <ReasonField label="Reason (kept in the audit log)" value={reason} onChange={setReason}
        placeholder={blocking ? 'e.g. Repeated fake bookings' : 'e.g. Identity confirmed by phone'} />
    </FormDialog>
  );
}

export function UsersRolesPage() {
  const [text, setText] = useUrlState<string>('q', '');
  const [role, setRole] = useUrlState<AdminRole | ''>('role', '', ['', ...ROLES]);
  const [editing, setEditing] = useState<AdminUser>();
  const [blocking, setBlocking] = useState<AdminUser>();
  const { showToast, toast } = useToast();
  const { mark, flashOf, withFlash } = useFlash();
  const { data, error, loading, reload } = useDispatchData(listUsers, []);
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
  const { rows: sortedRows, sort, toggle } = useSort(rows, {
    name: (u) => personName(u.name, u.email || searchedAs[u.id]).display, email: (u) => u.email || searchedAs[u.id], roles: (u) => u.roles.join(' '), state: (u) => (u.active ? 0 : 1),
  });
  const selfId = currentUserId();
  const adminCount = all?.filter((u) => u.roles.includes('ADMIN')).length ?? 0;

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

  const done = (message: string, options?: ToastOptions, changed?: string) => {
    setEditing(undefined);
    setBlocking(undefined);
    showToast(message, withFlash(options, changed));
    mark(changed);
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

      {error && <LoadError message={error} onRetry={reload} />}

      <div className={styles.tableCard} aria-busy={loading}>
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <SortHeader label="Name" column="name" sort={sort} onSort={toggle} />
                <SortHeader label="Email" column="email" sort={sort} onSort={toggle} />
                <SortHeader label="Roles" column="roles" sort={sort} onSort={toggle} />
                <SortHeader label="State" column="state" sort={sort} onSort={toggle} />
                <th scope="col"><span className={styles.visuallyHidden}>Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {sortedRows?.map((u) => (
                <tr key={u.id} data-flash={flashOf(u.id)}>
                  <td><PersonName name={u.name} email={u.email || searchedAs[u.id]} id={u.id} /></td>
                  <td>{u.email || searchedAs[u.id] ? <Email address={u.email || searchedAs[u.id]} /> : <span className={styles.muted}>—</span>}</td>
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

      {editing && <RolesDialog user={editing} selfId={selfId} adminCount={adminCount} onClose={() => setEditing(undefined)} onSaved={done} />}
      {blocking && <BlockDialog user={blocking} selfId={selfId} onClose={() => setBlocking(undefined)} onSaved={done} />}
      {toast}
    </>
  );
}
