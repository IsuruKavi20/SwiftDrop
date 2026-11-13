import { AccountPage } from '../features/account/pages/AccountPage';
import { GoogleCallbackPage } from '../features/auth/pages/GoogleCallbackPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { SignupPage } from '../features/auth/pages/SignupPage';
import { CustomerPortal, PublicTrackingPage } from '../features/customer/pages/CustomerPortal';
import { VerifyEmailPage } from '../features/auth/pages/VerifyEmailPage';
import { routes, usePath } from './router';

export default function App() {
  const path = usePath();
  if (path === routes.publicTrack || path.startsWith('/track/')) return <PublicTrackingPage />;
  if (path.startsWith('/customer/')) return <CustomerPortal />;
  switch (path) {
    case routes.signup:
      return <SignupPage />;
    case routes.googleCallback:
      return <GoogleCallbackPage />;
    case routes.verifyEmail:
      return <VerifyEmailPage />;
    case routes.account:
      return <AccountPage />;
    default:
      return <LoginPage />;
  }
}
