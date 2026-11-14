import { AccountPage } from '../features/account/pages/AccountPage';
import { GoogleCallbackPage } from '../features/auth/pages/GoogleCallbackPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { SignupPage } from '../features/auth/pages/SignupPage';
import { CustomerPortal, PublicTrackingPage } from '../features/customer/pages/CustomerPortal';
import { NotificationsPage } from '../features/notification/pages/NotificationsPage';
import { MerchantApplyPage } from '../features/merchant/pages/MerchantApplyPage';
import { VerifyEmailPage } from '../features/auth/pages/VerifyEmailPage';
import { ProtectedMerchantRoute } from '../features/merchant/components/ProtectedMerchantRoute';
import { MerchantPortal } from '../features/merchant/MerchantPortal';
import { matchMerchantRoute } from '../features/merchant/merchantRoutes';
import { routes, usePath } from './router';
import { LandingPage } from '../features/landing/pages/LandingPage';

export default function App() {
  const path = usePath();
  if (path === routes.customerNotifications) return <NotificationsPage />;
  if (path === routes.publicTrack || path.startsWith('/track/')) return <PublicTrackingPage />;
  if (path.startsWith('/customer/')) return <CustomerPortal />;

  const merchantRoute = matchMerchantRoute(path);
  if (merchantRoute) {
    if (merchantRoute.preview) return <MerchantPortal route={merchantRoute} />;
    return (
      <ProtectedMerchantRoute>
        {(claims) => <MerchantPortal route={merchantRoute} claims={claims} />}
      </ProtectedMerchantRoute>
    );
  }

  switch (path) {
    case routes.signup:
      return <SignupPage />;
    case routes.googleCallback:
      return <GoogleCallbackPage />;
    case routes.verifyEmail:
      return <VerifyEmailPage />;
    case routes.account:
      return <AccountPage />;
    case routes.merchantApply:
      return <MerchantApplyPage />;
    case routes.login:
      return <LoginPage />;
    // The home page (and any unknown address): tracking for everyone, login only when needed
    default:
      return <LandingPage />;
  }
}
