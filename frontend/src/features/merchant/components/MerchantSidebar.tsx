import { merchantPages, merchantPath, type MerchantPageId } from '../merchantRoutes';
import { MerchantIcon } from './MerchantIcon';
import { MerchantLink } from './MerchantLink';
import styles from './MerchantLayout.module.css';

interface MerchantSidebarProps {
  page: MerchantPageId;
  preview: boolean;
  onSignOut?: () => void;
}

export function MerchantSidebar({ page, preview, onSignOut }: MerchantSidebarProps) {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <span className={styles.brandIcon}><MerchantIcon name="box" size={23} /></span>
        <span><strong>SwiftDrop</strong><small>Merchant Portal</small></span>
      </div>
      <nav aria-label="Merchant navigation">
        <ul className={styles.navigation}>
          {merchantPages.map(({ id, title, icon }) => (
            <li key={id}>
              <MerchantLink className={styles.navItem} href={merchantPath(id, preview)} aria-current={page === id ? 'page' : undefined}>
                <MerchantIcon name={icon} /> {title}
              </MerchantLink>
            </li>
          ))}
        </ul>
      </nav>
      {onSignOut && <div className={styles.sidebarFooter}>
        <button className={styles.navItem} type="button" onClick={onSignOut}><MerchantIcon name="logout" /> Sign Out</button>
      </div>}
    </aside>
  );
}
