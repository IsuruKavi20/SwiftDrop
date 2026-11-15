import type { ReactNode } from 'react';
import { navigate, routes } from '../../../app/router';
import { logOut } from '../../auth/api/authApi';
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

/** Sidebar counts: merchant applications to review, parcels waiting for a courier, and failed deliveries. */
async function loadNavCounts(): Promise<Partial<Record<DispatcherPageId, number>>> {
  const [pending, unassigned, counts] = await Promise.all([
    listMerchantApplications('PENDING'), countUnassignedDeliveries(), getStatusCounts(),
  ]);
  return { merchants: pending.length, unassigned, failed: counts.FAILED };
}

export function DispatcherLayout({ page, preview, name, children }: DispatcherLayoutProps) {
  const { data: counts } = useDispatchData(loadNavCounts, []);
  const section = dispatcherPages.find((p) => p.id === page)?.section;

  function signOut() {
    void logOut();
    navigate(routes.login, { replace: true });
  }

  const initials = name?.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'A';

  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>
          <span className={styles.brandIcon}><DispatcherIcon name="box" size={22} /></span>
          <strong>SwiftDrop</strong>
          <span className={styles.roleChip}>Admin</span>
        </div>

        <nav aria-label="Admin navigation">
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
                        <DispatcherIcon name={icon} />
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
        {(usingSampleData() || section === 'Admin') && (
          <p className={styles.notice} role="note">
            {usingSampleData()
              ? 'Development preview: you are not signed in, so these screens show sample data and changes stay in this browser tab.'
              : 'This admin page still shows sample data: its backend is not built yet, so changes stay in this browser tab. The Dispatch pages are live.'}
          </p>
        )}
        {children}
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
  return (
    <header className={styles.pageHeader}>
      <div>
        {breadcrumb && (
          <nav className={styles.breadcrumb} aria-label="Breadcrumb">
            <DispatcherLink href={dispatcherPath('operations', preview)}>Operations</DispatcherLink>
            <DispatcherIcon name="right" size={14} />
            <span aria-current="page">{title}</span>
          </nav>
        )}
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}
