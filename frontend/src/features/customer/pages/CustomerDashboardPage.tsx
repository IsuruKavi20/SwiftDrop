import { useCallback, useEffect, useRef, useState } from 'react';
import logoIcon from '../../../assets/auth/logo-box.svg';
import userIcon from '../../../assets/auth/icon-user.svg';
import { navigate, routes } from '../../../app/router';
import { fetchAccountName, logOut, refresh } from '../../auth/api/authApi';
import { Alert } from '../../auth/components/FormControls';
import { getAccessToken, readClaims } from '../../auth/lib/session';
import {
  ApiError, getDashboard, getProfile, STATUS_LABELS,
  type CustomerProfile, type Dashboard, type DeliveryStatus,
} from '../api/customerApi';
import { CompleteProfileForm } from '../components/CompleteProfileForm';
import styles from '../components/CustomerLayout.module.css';

type State =
  | { kind: 'loading' }
  | { kind: 'needsProfile' }
  | { kind: 'ready'; profile: CustomerProfile; dashboard: Dashboard }
  | { kind: 'error'; message: string };

/** Customer features in the dashboard menu; the pages are still being built. */
const FEATURES = [
  { title: 'Book a delivery', text: 'Send a parcel anywhere in the country.' },
  { title: 'My deliveries', text: 'See and search all your deliveries.' },
  { title: 'Track a parcel', text: 'Follow a parcel with its tracking number.' },
];

/**
 * Customer menu: the sidebar on desktop, the bottom bar on mobile (with the short label). Only Overview (this page)
 * exists so far; the rest are disabled until their pages are built. Icon paths are 24x24 line icons.
 */
const NAV_ITEMS = [
  { label: 'Overview', short: 'Home', active: true, icon: ['M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z'] },
  { label: 'Send a parcel', short: 'Send', active: false, icon: ['M22 2 11 13', 'M22 2 15 22l-4-9-9-4z'] },
  { label: 'My deliveries', short: 'Deliveries', active: false, icon: ['M21 8 12 3 3 8v8l9 5 9-5z', 'M3 8l9 5 9-5', 'M12 13v8'] },
  {
    label: 'Track parcel', short: 'Track', active: false,
    icon: ['M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z', 'M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z'],
  },
  { label: 'My account', short: 'Account', active: false, icon: ['M20 21a8 8 0 0 0-16 0', 'M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10z'] },
];

/** Icons used outside the menu list (navbar bell, sidebar footer). */
const ICONS = {
  bell: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  settings: [
    'M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z',
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  ],
  logOut: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
};

function LineIcon({ paths, size = 20 }: { paths: string[]; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {paths.map((d) => <path key={d} d={d} />)}
    </svg>
  );
}

const STATUS_TONE: Record<DeliveryStatus, string> = {
  PENDING: styles.toneNeutral,
  CONFIRMED: styles.toneInfo,
  PICKED_UP: styles.toneInfo,
  IN_TRANSIT: styles.toneInfo,
  OUT_FOR_DELIVERY: styles.toneInfo,
  DELIVERED: styles.toneSuccess,
  FAILED: styles.toneError,
  CANCELLED: styles.toneNeutral,
};

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
}

/** The customer's home page after login: delivery counts, recent deliveries and the way to every customer feature. */
export function CustomerDashboardPage() {
  const [state, setState] = useState<State>({ kind: 'loading' });
  // The name from the auth service's userinfo, fetched only when neither the profile nor the token has one
  const [fetchedName, setFetchedName] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState({ kind: 'loading' });
    try {
      const [profile, dashboard] = await Promise.all([getProfile(), getDashboard()]);
      setState({ kind: 'ready', profile, dashboard });
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 404) {
        setState({ kind: 'needsProfile' }); // signed up, but no SwiftDrop customer profile yet
      } else if (failure instanceof ApiError && failure.status === 401) {
        navigate(routes.login);
      } else {
        setState({ kind: 'error', message: failure instanceof Error ? failure.message : 'Could not load your dashboard.' });
      }
    }
  }, []);

  useEffect(() => {
    (async () => {
      if (!getAccessToken() && !(await refresh())) {
        navigate(routes.login); // not logged in (or the session ended)
        return;
      }
      if (!(readClaims(getAccessToken()!).entitlements ?? []).includes('parcel:read-own')) {
        navigate(routes.account); // logged in, but not a customer
        return;
      }
      if (!readNameFromToken()) {
        void fetchAccountName().then(setFetchedName);
      }
      await load();
    })();
  }, [load]);

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  const profile = state.kind === 'ready' ? state.profile : null;
  // The full name the customer entered; before the profile exists, the login account's name if it has one
  const name = profile?.name || readNameFromToken() || fetchedName;

  return (
    <div className={styles.page}>
      <aside className={styles.sidebar}>
        <div className={styles.logo}>
          <span className={styles.logoMark}><img src={logoIcon} width={20} height={20} alt="" /></span>
          SwiftDrop
        </div>

        <nav className={styles.nav} aria-label="Customer menu">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.label}
              type="button"
              className={`${styles.navItem} ${item.active ? styles.navItemActive : ''}`}
              aria-current={item.active ? 'page' : undefined}
              disabled={!item.active}
              title={item.active ? undefined : 'Coming soon'}
            >
              <LineIcon paths={item.icon} />
              {item.label}
            </button>
          ))}
        </nav>

        {/* Pinned to the bottom of the sidebar. No notifications or settings pages yet: disabled until they exist */}
        <div className={styles.sidebarFooter}>
          <button type="button" className={styles.navItem} disabled title="Coming soon">
            <LineIcon paths={ICONS.bell} />
            Notifications
          </button>
          <button type="button" className={styles.navItem} disabled title="Coming soon">
            <LineIcon paths={ICONS.settings} />
            Settings
          </button>
          <button type="button" className={`${styles.navItem} ${styles.navItemDanger}`} onClick={() => void onLogOut()}>
            <LineIcon paths={ICONS.logOut} />
            Log out
          </button>
        </div>
      </aside>

      <div className={styles.content}>
        <header className={styles.navbar}>
          {/* Mobile only: the sidebar (and its logo) is hidden there */}
          <span className={`${styles.logoMark} ${styles.navbarLogo}`}>
            <img src={logoIcon} width={20} height={20} alt="SwiftDrop" />
          </span>
          <div className={styles.navbarTitle}>
            <h1 className={styles.pageTitle}>Customer Dashboard</h1>
            <p className={styles.welcome}>
              {name ? <>Welcome back, <strong>{name}</strong></> : 'Welcome back'}
            </p>
          </div>

          <div className={styles.navbarActions}>
            {/* No notifications service yet: the bell is a placeholder until one exists */}
            <button type="button" className={styles.iconButton} aria-label="Notifications" title="No new notifications">
              <LineIcon paths={ICONS.bell} />
            </button>
            <ProfileMenu name={name} pictureUrl={profile?.pictureUrl ?? null} onLogOut={() => void onLogOut()} />
          </div>
        </header>

        <main className={styles.main}>
          {state.kind === 'loading' && <p className={styles.muted} role="status">Loading your dashboard…</p>}

          {state.kind === 'error' && (
            <section className={styles.card}>
              <Alert kind="error">{state.message}</Alert>
              <button type="button" className={styles.ghostButton} onClick={() => void load()}>Try again</button>
            </section>
          )}

          {state.kind === 'needsProfile' && <CompleteProfileForm onRegistered={() => void load()} />}

          {state.kind === 'ready' && <DashboardView dashboard={state.dashboard} />}
        </main>
      </div>

      {/* Mobile only: the customer menu as a bottom bar */}
      <nav className={styles.bottomNav} aria-label="Customer menu">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.label}
            type="button"
            className={`${styles.bottomNavItem} ${item.active ? styles.bottomNavItemActive : ''}`}
            aria-current={item.active ? 'page' : undefined}
            aria-label={item.label}
            disabled={!item.active}
            title={item.active ? undefined : 'Coming soon'}
          >
            <LineIcon paths={item.icon} size={22} />
            <span>{item.short}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}

/**
 * Avatar button that opens the account menu (Settings, Log out). Always shown, even before the profile loads,
 * so a customer without a profile can still log out. Closes on an outside click or Escape.
 */
function ProfileMenu({ name, pictureUrl, onLogOut }: {
  name: string | null;
  pictureUrl: string | null;
  onLogOut: () => void;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div className={styles.profileMenu} ref={menuRef}>
      <button
        type="button"
        className={styles.profileButton}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span className={styles.avatar} aria-hidden="true">
          {pictureUrl
            // The photo from the login account (e.g. Google); no-referrer: Google may refuse images requested
            // with another site's address, and it keeps our URLs private
            ? <img className={styles.avatarPhoto} src={pictureUrl} alt="" referrerPolicy="no-referrer" />
            : name
              ? initials(name)
              : <img src={userIcon} width={18} height={18} alt="" />}
        </span>
        <span className={styles.userName}>{name ?? 'Account'}</span>
        <svg className={styles.caret} width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className={styles.dropdown} role="menu">
          {name && (
            <div className={styles.dropdownHeader}>
              <span className={styles.dropdownName}>{name}</span>
              <span className={styles.dropdownEmail}>{loginEmail()}</span>
            </div>
          )}
          {/* No settings page yet: enable and navigate once it exists */}
          <button type="button" role="menuitem" className={styles.dropdownItem} disabled title="Coming soon">
            Settings
          </button>
          <button
            type="button"
            role="menuitem"
            className={`${styles.dropdownItem} ${styles.dropdownDanger}`}
            onClick={() => { setOpen(false); onLogOut(); }}
          >
            Log out
          </button>
        </div>
      )}
    </div>
  );
}


function DashboardView({ dashboard }: { dashboard: Dashboard }) {
  const counts = dashboard.countsByStatus;
  const stats = [
    { label: 'Total deliveries', value: dashboard.totalDeliveries },
    { label: 'On the way', value: dashboard.activeDeliveries },
    { label: 'Awaiting confirmation', value: counts.PENDING },
    { label: 'Delivered', value: counts.DELIVERED },
  ];

  return (
    <>
      <section>
        <p className={styles.muted}>Here is what is happening with your parcels.</p>
      </section>

      <section className={styles.stats} aria-label="Delivery summary">
        {stats.map((stat) => (
          <div key={stat.label} className={styles.stat}>
            <span className={styles.statValue}>{stat.value}</span>
            <span className={styles.statLabel}>{stat.label}</span>
          </div>
        ))}
      </section>

      <section className={styles.features} aria-label="Customer features">
        {FEATURES.map((feature) => (
          <button key={feature.title} type="button" className={styles.feature} disabled title="Coming soon">
            <span className={styles.featureTitle}>{feature.title}</span>
            <span className={styles.muted}>{feature.text}</span>
            <span className={styles.soon}>Coming soon</span>
          </button>
        ))}
      </section>

      <section className={styles.card}>
        <h2 className={styles.cardTitle}>Recent deliveries</h2>
        {dashboard.recentDeliveries.length === 0 ? (
          <p className={styles.muted}>You have no deliveries yet. Your bookings will show up here.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Tracking number</th>
                  <th>Receiver</th>
                  <th>Destination</th>
                  <th>Status</th>
                  <th>Estimated delivery</th>
                </tr>
              </thead>
              <tbody>
                {dashboard.recentDeliveries.map((delivery) => (
                  <tr key={delivery.id}>
                    <td className={styles.mono}>{delivery.trackingNumber}</td>
                    <td>{delivery.receiverName ?? '—'}</td>
                    <td>{delivery.destinationCity ?? '—'}</td>
                    <td>
                      <span className={`${styles.badge} ${STATUS_TONE[delivery.status]}`}>
                        {STATUS_LABELS[delivery.status]}
                      </span>
                    </td>
                    <td>{formatDate(delivery.estimatedDeliveryDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

// Name and email belong to the login account: the profile or token has the name, the token has the email
function readNameFromToken(): string | undefined {
  const token = getAccessToken();
  return (token ? readClaims(token).name?.trim() : undefined) || undefined;
}

function loginEmail(): string {
  const token = getAccessToken();
  return (token ? readClaims(token).email : undefined) ?? '';
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.charAt(0) ?? '') + (parts.length > 1 ? parts[parts.length - 1].charAt(0) : '')).toUpperCase();
}
