import { routes } from '../../../app/router';
import { merchantPath } from '../merchantRoutes';
import { MerchantIcon } from './MerchantIcon';
import { MerchantLink } from './MerchantLink';
import styles from './MerchantLayout.module.css';

interface MerchantHeaderProps {
  name?: string;
  title: string;
  preview: boolean;
  onSignOut?: () => void;
}

export function MerchantHeader({ name, title, preview, onSignOut }: MerchantHeaderProps) {
  const initials = name?.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'M';

  return (
    <header className={styles.header}>
      <div>
        <h1>{title}</h1>
        {name && <p>Signed in as {name}</p>}
      </div>
      <div className={styles.toolbar}>
        <MerchantLink className={styles.create} href={merchantPath('create-order', preview)}>
          <MerchantIcon name="plus" size={18} /> Create Order
        </MerchantLink>
        {!preview && <details className={styles.account}>
          <summary>
            <span className={styles.avatar} aria-hidden="true">{initials}</span>
            <span><strong>{name || 'Merchant'}</strong><small>Merchant account</small></span>
            <MerchantIcon name="down" size={15} />
          </summary>
          <div className={styles.accountMenu}>
            <MerchantLink href={routes.account}>Account</MerchantLink>
            <button type="button" onClick={onSignOut}>Sign Out</button>
          </div>
        </details>}
      </div>
    </header>
  );
}
