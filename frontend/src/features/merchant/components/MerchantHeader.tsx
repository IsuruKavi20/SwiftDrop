import { routes } from '../../../app/router';
import type { AccessClaims } from '../../auth/lib/session';
import { merchantPath } from '../merchantRoutes';
import { MerchantIcon } from './MerchantIcon';
import { MerchantLink } from './MerchantLink';
import styles from './MerchantLayout.module.css';

interface MerchantHeaderProps {
  claims?: AccessClaims;
  name?: string;
  title: string;
  preview: boolean;
  onSignOut?: () => void;
}

export function MerchantHeader({ claims, name, title, preview, onSignOut }: MerchantHeaderProps) {
  const initials = name?.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'M';
  const hasCustomerAccess = Boolean(claims?.entitlements?.includes('parcel:read-own'));

  return (
    <header className={styles.header}>
      <h1>{title}</h1>
      <div className={styles.toolbar}>
        <MerchantLink className={styles.create} href={merchantPath('create-order', preview)}>
          <MerchantIcon name="plus" size={18} /> Create Order
        </MerchantLink>
        {!preview && <details className={styles.account}>
          <summary>
            <span className={styles.avatar} aria-hidden="true">{initials}</span>
            <span className={styles.accountIdentity}><strong>{name || 'Merchant'}</strong><small>Merchant account</small></span>
            <MerchantIcon name="down" size={15} />
          </summary>
          <div className={styles.accountMenu}>
            {hasCustomerAccess && (
              <MerchantLink href={routes.customerDashboard}>Customer View</MerchantLink>
            )}
            <MerchantLink href={routes.account}>Account</MerchantLink>
            <button type="button" onClick={onSignOut}>Sign Out</button>
          </div>
        </details>}
      </div>
    </header>
  );
}
