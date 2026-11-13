import { AccountPage } from '../features/account/pages/AccountPage';
import { GoogleCallbackPage } from '../features/auth/pages/GoogleCallbackPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { SignupPage } from '../features/auth/pages/SignupPage';
import { MerchantApplyPage } from '../features/merchant/pages/MerchantApplyPage';
import { CustomerDashboardPage } from '../features/customer/pages/CustomerDashboardPage';
import { VerifyEmailPage } from '../features/auth/pages/VerifyEmailPage';
import { routes, usePath } from './router';

export default function App() {
  const path = usePath();
  switch (path) {
    case routes.signup:
      return <SignupPage />;
    case routes.googleCallback:
      return <GoogleCallbackPage />;
    case routes.verifyEmail:
      return <VerifyEmailPage />;
    case routes.account:
      return <AccountPage />;
    case routes.customerDashboard:
      return <CustomerDashboardPage />;
    case routes.merchantApply:
      return <MerchantApplyPage />;
    default:
      return <LoginPage />;
  }
}
