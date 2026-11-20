import { useEffect, useState } from 'react';
import { navigate, routes } from '../../../app/router';
import { logOut, refresh } from '../../auth/api/authApi';
import { getAccessToken } from '../../auth/lib/session';
import { currentAreas } from '../../auth/lib/areas';
import { AuthLayout } from '../../auth/components/AuthLayout';
import { Alert } from '../../auth/components/FormControls';
import styles from '../../auth/components/FormControls.module.css';

/**
 * /account: sends a signed-in user to their main area (admin, merchant or customer). Only a login with no
 * screens yet stays here. Replaces the old developer page that showed ids and permissions.
 */
export function AccountPage() {
  const [noArea, setNoArea] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      if (!getAccessToken() && !(await refresh())) {
        navigate(routes.login, { replace: true });
        return;
      }
      const [main] = currentAreas();
      if (!active) return;
      if (main) navigate(main.path, { replace: true });
      else setNoArea(true);
    })();
    return () => {
      active = false;
    };
  }, []);

  async function onLogOut() {
    await logOut();
    navigate(routes.login);
  }

  return (
    <AuthLayout headline="Welcome back." tagline="You are signed in to SwiftDrop." eyebrow="ACCOUNT"
      title={noArea ? 'Nothing to open yet' : 'Opening SwiftDrop…'} subtitle="">
      {noArea && (
        <>
          <Alert kind="error">Your account has no SwiftDrop screens yet. Ask an admin if you think this is wrong.</Alert>
          <button type="button" className={styles.link} onClick={() => void onLogOut()}>Log out</button>
        </>
      )}
    </AuthLayout>
  );
}
