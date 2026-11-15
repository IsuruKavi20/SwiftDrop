import type { AccessClaims } from '../auth/lib/session';
import { MerchantLayout } from './components/MerchantLayout';
import type { matchMerchantRoute } from './merchantRoutes';
import { BusinessProfilePage } from './pages/BusinessProfilePage';
import { CreateOrderPage } from './pages/CreateOrderPage';
import { OrderDetailsPage, StoreOrdersPage } from './pages/StoreOrdersPage';
import { MerchantDashboardPage } from './pages/MerchantDashboardPage';
import { MerchantUnavailablePage } from './pages/MerchantUnavailablePage';
import { TrackingPage } from './pages/TrackingPage';
import { ApiCredentialsPage } from './pages/ApiCredentialsPage';
import { WebhooksPage } from './pages/WebhooksPage';

type PortalRoute = NonNullable<ReturnType<typeof matchMerchantRoute>>;

export function MerchantPortal({ route, claims }: { route: PortalRoute; claims?: AccessClaims }) {
  const { page, preview, orderId } = route;

  function renderPage() {
    switch (page.id) {
      case 'dashboard':
        return <MerchantDashboardPage preview={preview} />;
      case 'orders':
        return orderId ? (
          <OrderDetailsPage orderId={orderId} preview={preview} />
        ) : (
          <StoreOrdersPage preview={preview} />
        );
      case 'create-order':
        return <CreateOrderPage preview={preview} />;
      case 'business-profile':
        return <BusinessProfilePage key={String(preview)} preview={preview} />;
      case 'tracking':
        return <TrackingPage />;
      case 'api-credentials':
        return <ApiCredentialsPage preview={preview} />;
      case 'webhooks':
        return <WebhooksPage preview={preview} />;
      default:
        return <MerchantUnavailablePage page={page.id} preview={preview} />;
    }
  }

  return (
    <MerchantLayout
      name={claims?.name?.trim()}
      title={orderId ? 'Order Details' : page.id === 'dashboard' ? 'Merchant Dashboard' : page.title}
      page={page.id}
      preview={preview}
    >
      {renderPage()}
    </MerchantLayout>
  );
}
