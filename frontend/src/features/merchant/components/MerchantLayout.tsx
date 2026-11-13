import type { ReactNode } from 'react';
import { navigate, routes } from '../../../app/router';
import { logOut } from '../../auth/api/authApi';
import type { MerchantPageId } from '../merchantRoutes';
import { MerchantHeader } from './MerchantHeader';
import { MerchantSidebar } from './MerchantSidebar';
import styles from './MerchantLayout.module.css';

interface MerchantLayoutProps {
  name?: string;
  title: string;
  page: MerchantPageId;
  preview: boolean;
  children: ReactNode;
}

export function MerchantLayout({ name, title, page, preview, children }: MerchantLayoutProps) {
  function signOut() {
    void logOut();
    navigate(routes.login, { replace: true });
  }

  const onSignOut = preview ? undefined : signOut;
  return (
    <div className={styles.layout}>
      <MerchantSidebar page={page} preview={preview} onSignOut={onSignOut} />
      <main className={styles.main}>
        <MerchantHeader name={name} title={title} preview={preview} onSignOut={onSignOut} />
        {preview && <p className={styles.previewNotice}>Development preview. Sign in to load or save your business profile.</p>}
        {children}
      </main>
    </div>
  );
}
