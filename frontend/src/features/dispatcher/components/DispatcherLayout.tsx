import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import logoMark from '../../../assets/brand/logo-mark.svg';
import { navigate, routes } from '../../../app/router';
import { logOut } from '../../auth/api/authApi';
import { otherAreas } from '../../auth/lib/areas';
import { countUnassignedDeliveries, getStatusCounts, usingSampleData } from '../api/dispatcherApi';
import { listMerchantApplications } from '../api/adminApi';
import { dispatcherPages, dispatcherPath, dispatcherSections, type DispatcherPageId } from '../dispatcherRoutes';
import { useDispatchData } from '../lib/useDispatchData';
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
  const { data: counts } = useDispatchData(loadNavCounts, []);
  // The page the drawer was opened on: going to another page closes it without an extra render
  const [menuPage, setMenuPage] = useState<DispatcherPageId | null>(null);
  const menuOpen = menuPage === page;
  const setMenuOpen = (open: boolean) => setMenuPage(open ? page : null);
  const current = dispatcherPages.find((p) => p.id === page);

  // Escape closes the drawer
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuPage(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  function signOut() {
    void logOut();
    navigate(routes.login, { replace: true });
  }

  const initials = name?.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'A';

  return (
    <div className={styles.layout} data-menu-open={menuOpen || undefined}>
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
                        aria-current={page === id ? 'page' : undefined}>
                        <span className={styles.navIcon}><DispatcherIcon name={icon} size={18} /></span>
                        <span className={styles.navLabel}>{title}</span>
                        {count ? <span className={styles.navCount} aria-label={`${count} waiting`}>{count}</span> : null}
                      </DispatcherLink>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {!preview && otherAreas('admin').filter((area) => area.id === 'customer').map((area) => (
          <button key={area.id} type="button" className={`${styles.navItem} ${styles.switchView}`} onClick={() => navigate(area.path)}>
            <DispatcherIcon name="user" />
            <span className={styles.navLabel}>Customer view</span>
          </button>
        ))}

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

      <main className={styles.main}>
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
