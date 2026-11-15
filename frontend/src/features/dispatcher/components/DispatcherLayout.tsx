import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import logoMark from '../../../assets/brand/logo-mark.svg';
import { navigate, routes } from '../../../app/router';
import { logOut } from '../../auth/api/authApi';
import { otherAreas } from '../../auth/lib/areas';
import { countUnassignedDeliveries, getStatusCounts, usingSampleData } from '../api/dispatcherApi';
import { listMerchantApplications } from '../api/adminApi';
import { dispatcherPages, dispatcherPath, dispatcherSections, type DispatcherPageId } from '../dispatcherRoutes';
import { useLiveCounts } from '../lib/useLiveCounts';
import { useTableLabels } from '../lib/useTableLabels';
import { CommandPalette } from './CommandPalette';
import { DispatcherIcon } from './DispatcherIcon';
import { DispatcherLink } from './DispatcherLink';
import styles from './Dispatcher.module.css';

interface DispatcherLayoutProps {
  page: DispatcherPageId;
  preview: boolean;
  name?: string;
  children: ReactNode;
}

/** The sidebar section of the open page ("Admin" or "Dispatch"), shown as the eyebrow above each page title. */
const SectionContext = createContext<string | undefined>(undefined);

const COLLAPSED_KEY = 'swiftdrop.admin.sidebarCollapsed';

/** Browser storage can be blocked (private mode, site settings): then the sidebar simply starts expanded. */
function readCollapsed(): boolean {
  try { return localStorage.getItem(COLLAPSED_KEY) === '1'; } catch { return false; }
}
function writeCollapsed(collapsed: boolean) {
  try { localStorage.setItem(COLLAPSED_KEY, collapsed ? '1' : '0'); } catch { /* not remembered; still works */ }
}

/** Sidebar counts: merchant applications to review, parcels waiting for a courier, and failed deliveries. */
async function loadNavCounts(): Promise<Partial<Record<DispatcherPageId, number>>> {
  // Each count on its own: if the auth service is down, the dispatch counts still show
  const [pending, unassigned, counts] = await Promise.allSettled([
    listMerchantApplications('PENDING'), countUnassignedDeliveries(), getStatusCounts(),
  ]);
  return {
    merchants: pending.status === 'fulfilled' ? pending.value.length : undefined,
    unassigned: unassigned.status === 'fulfilled' ? unassigned.value : undefined,
    failed: counts.status === 'fulfilled' ? counts.value.FAILED : undefined,
  };
}

/**
 * The admin area's frame, in the login page's style: a dark brand sidebar (dotted texture, gold glow, dashed gold
 * route) beside the cream workspace. On phones and tablets the sidebar becomes a drawer opened from a top bar.
 */
export function DispatcherLayout({ page, preview, name, children }: DispatcherLayoutProps) {
  // Sidebar counts, refreshed every minute while the tab is open; `grown` marks counts that just went up
  const { counts, grown } = useLiveCounts(loadNavCounts);
  const [dismissed, setDismissed] = useState<Partial<Record<DispatcherPageId, number>>>();
  const newParcels = grown !== dismissed ? grown.unassigned : undefined;
  // The page the drawer was opened on: going to another page closes it without an extra render
  const [menuPage, setMenuPage] = useState<DispatcherPageId | null>(null);
  const menuOpen = menuPage === page;
  const setMenuOpen = (open: boolean) => setMenuPage(open ? page : null);
  const current = dispatcherPages.find((p) => p.id === page);
  // Desktop: the sidebar can shrink to icons only; remembered in this browser
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const toggleCollapsed = () => setCollapsed((was) => { writeCollapsed(!was); return !was; });
  // Column names on every table cell, for the phone card layout
  const mainRef = useRef<HTMLElement>(null);
  useTableLabels(mainRef, styles.table, styles.visuallyHidden);

  // Escape closes the drawer
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuPage(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  // Keyboard shortcuts: Ctrl/Cmd+K go to a page, "/" search this page, "?" list the shortcuts
  const [palette, setPalette] = useState<'go' | 'help' | null>(null);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      const typing = Boolean(target?.closest('input, textarea, select, [contenteditable="true"]'));
      const inDialog = Boolean(document.querySelector('dialog[open]'));
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        if (!inDialog || palette) setPalette((open) => (open ? null : 'go'));
        return;
      }
      if (typing || inDialog || event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.key === '/') {
        const search = mainRef.current?.querySelector<HTMLInputElement>('input[data-page-search]');
        if (search) { event.preventDefault(); search.focus(); search.select(); }
      } else if (event.key === '?') {
        event.preventDefault();
        setPalette('help');
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [palette]);

  function signOut() {
    void logOut();
    navigate(routes.login, { replace: true });
  }

  const initials = name?.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'A';

  return (
    <div className={styles.layout} data-menu-open={menuOpen || undefined} data-collapsed={collapsed || undefined}>
      <a className={styles.skipLink} href="#admin-main">Skip to content</a>
      <header className={styles.topBar}>
        <button className={styles.menuButton} type="button" onClick={() => setMenuOpen(true)}
          aria-label="Open menu" aria-expanded={menuOpen} aria-controls="admin-sidebar">
          <DispatcherIcon name="menu" size={22} />
        </button>
        <span className={styles.topBrand}>
          <img src={logoMark} width={26} height={26} alt="" />
          SwiftDrop
        </span>
        <span className={styles.topPage}>{current?.title}</span>
      </header>

      <button className={styles.scrim} type="button" tabIndex={-1} aria-hidden="true" onClick={() => setMenuOpen(false)} />

      <aside id="admin-sidebar" className={styles.sidebar}>
        <div className={styles.brand}>
          <span className={styles.brandIcon}><img src={logoMark} width={30} height={30} alt="" /></span>
          <strong>SwiftDrop</strong>
          <span className={styles.roleChip}>Admin</span>
          <button className={styles.closeMenu} type="button" onClick={() => setMenuOpen(false)} aria-label="Close menu">
            <DispatcherIcon name="close" size={20} />
          </button>
        </div>

        <nav aria-label="Admin navigation" className={styles.sideNav}>
          {dispatcherSections.map((section) => (
            <div key={section} className={styles.navSection}>
              <h2 className={styles.navHeading}>{section}</h2>
              <ul className={styles.navigation}>
                {dispatcherPages.filter((p) => p.section === section).map(({ id, title, icon }) => {
                  const count = counts?.[id];
                  return (
                    <li key={id}>
                      <DispatcherLink className={styles.navItem} href={dispatcherPath(id, preview)}
                        aria-current={page === id ? 'page' : undefined} title={collapsed ? title : undefined}>
                        <span className={styles.navIcon}><DispatcherIcon name={icon} size={18} /></span>
                        <span className={styles.navLabel}>{title}</span>
                        {count ? <span className={styles.navCount} data-new={grown[id] ? true : undefined} aria-label={`${count} waiting`}>{count}</span> : null}
                      </DispatcherLink>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {!preview && otherAreas('admin').filter((area) => area.id === 'customer').map((area) => (
          <button key={area.id} type="button" className={`${styles.navItem} ${styles.switchView}`} onClick={() => navigate(area.path)}
            title={collapsed ? 'Customer view' : undefined}>
            <span className={styles.navIcon}><DispatcherIcon name="user" size={18} /></span>
            <span className={styles.navLabel}>Customer view</span>
          </button>
        ))}

        <button className={styles.collapseButton} type="button" onClick={toggleCollapsed}
          aria-expanded={!collapsed} title={collapsed ? 'Expand sidebar' : undefined}>
          <DispatcherIcon name={collapsed ? 'right' : 'left'} size={18} />
          <span className={styles.collapseLabel}>{collapsed ? 'Expand sidebar' : 'Collapse'}</span>
        </button>

        <div className={styles.sidebarFooter}>
          <span className={styles.avatar} aria-hidden="true">{initials}</span>
          <span className={styles.who}>
            <strong>{name || (preview ? 'Preview' : 'Admin')}</strong>
            <small>{preview ? 'Not signed in' : 'Admin account'}</small>
          </span>
          {!preview && (
            <button className={styles.iconButton} type="button" onClick={signOut} aria-label="Sign out" title="Sign out">
              <DispatcherIcon name="logout" />
            </button>
          )}
        </div>
      </aside>

      <main ref={mainRef} id="admin-main" tabIndex={-1} className={styles.main}>
        {usingSampleData() && (
          <p className={styles.notice} role="note">
            <DispatcherIcon name="alert" size={16} />
            Development preview: you are not signed in, so these screens show sample data and changes stay in this browser tab.
          </p>
        )}
        <SectionContext.Provider value={current?.section}>
          <div className={styles.pageBody} key={page}>{children}</div>
        </SectionContext.Provider>
      </main>

      {newParcels && page !== 'unassigned' && (
        <div className={styles.liveNotice} role="status">
          <span className={styles.stateIcon} data-tone="amber"><DispatcherIcon name="inbox" size={18} /></span>
          <span>
            <strong>{newParcels === 1 ? 'A new parcel is' : `${newParcels} new parcels are`} waiting for a courier</strong>
            <small>{counts?.unassigned ?? newParcels} unassigned in total</small>
          </span>
          <DispatcherLink className={styles.primaryButton} href={dispatcherPath('unassigned', preview)}>Assign</DispatcherLink>
          <button className={styles.iconButton} type="button" onClick={() => setDismissed(grown)} aria-label="Dismiss">
            <DispatcherIcon name="close" size={16} />
          </button>
        </div>
      )}

      {palette && <CommandPalette preview={preview} showHelp={palette === 'help'} onClose={() => setPalette(null)} />}
    </div>
  );
}

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
  breadcrumb?: boolean;
  preview?: boolean;
  action?: ReactNode;
}

export function PageHeader({ title, subtitle, breadcrumb, preview = false, action }: PageHeaderProps) {
  const section = useContext(SectionContext);
  return (
    <header className={styles.pageHeader}>
      <div className={styles.pageHeading}>
        {breadcrumb ? (
          <nav className={styles.breadcrumb} aria-label="Breadcrumb">
            <DispatcherLink href={dispatcherPath('operations', preview)}>Operations</DispatcherLink>
            <DispatcherIcon name="right" size={14} />
            <span aria-current="page">{title}</span>
          </nav>
        ) : section && <p className={styles.eyebrow}>{section}</p>}
        <h1>{title}</h1>
        {subtitle && <p className={styles.pageSubtitle}>{subtitle}</p>}
      </div>
      {action && <div className={styles.pageAction}>{action}</div>}
    </header>
  );
}
