import { useEffect } from 'react';
import type { AccessClaims } from '../auth/lib/session';
import { setDispatchSource } from './api/dispatcherApi';
import { DispatcherLayout } from './components/DispatcherLayout';
import { ProtectedDispatcherRoute } from './components/ProtectedDispatcherRoute';
import type { DispatcherRoute } from './dispatcherRoutes';
import { AdminOverviewPage } from './pages/AdminOverviewPage';
import { AllDeliveriesPage } from './pages/AllDeliveriesPage';
import { CourierWorkloadsPage } from './pages/CourierWorkloadsPage';
import { CouriersPage } from './pages/CouriersPage';
import { DeliveryZonesPage } from './pages/DeliveryZonesPage';
import { FailedDeliveriesPage } from './pages/FailedDeliveriesPage';
import { MerchantApprovalsPage } from './pages/MerchantApprovalsPage';
import { OverviewPage } from './pages/OverviewPage';
import { PricingPage } from './pages/PricingPage';
import { ReportsPage } from './pages/ReportsPage';
import { UnassignedParcelsPage } from './pages/UnassignedParcelsPage';
import { UsersRolesPage } from './pages/UsersRolesPage';

function DispatcherPortal({ route, claims }: { route: DispatcherRoute; claims?: AccessClaims }) {
  const { page, preview } = route;

  useEffect(() => {
    document.title = `${page.title} · SwiftDrop Admin`;
  }, [page.title]);

  function renderPage() {
    switch (page.id) {
      case 'admin': return <AdminOverviewPage preview={preview} />;
      case 'users': return <UsersRolesPage />;
      case 'merchants': return <MerchantApprovalsPage />;
      case 'couriers': return <CouriersPage />;
      case 'zones': return <DeliveryZonesPage />;
      case 'pricing': return <PricingPage />;
      case 'reports': return <ReportsPage />;
      case 'operations': return <OverviewPage preview={preview} />;
      case 'unassigned': return <UnassignedParcelsPage preview={preview} />;
      case 'deliveries': return <AllDeliveriesPage preview={preview} />;
      case 'workloads': return <CourierWorkloadsPage preview={preview} />;
      case 'failed': return <FailedDeliveriesPage preview={preview} />;
    }
  }

  return (
    <DispatcherLayout page={page.id} preview={preview} name={claims?.name?.trim()}>
      {renderPage()}
    </DispatcherLayout>
  );
}

/**
 * The admin area (admin pages and dispatch pages) for a matched route. Signed-in pages need the ADMIN role's
 * user:read entitlement;
 * the development preview shows the screens without signing in.
 *
 * To add it to the app, in app/App.tsx before the switch:
 *   const dispatcherRoute = matchDispatcherRoute(path);
 *   if (dispatcherRoute) return <DispatcherArea route={dispatcherRoute} />;
 */
export function DispatcherArea({ route }: { route: DispatcherRoute }) {
  // Before any page loads data: the preview has no sign-in, so it uses sample data
  setDispatchSource(route.preview ? 'sample' : 'api');
  if (route.preview) return <DispatcherPortal route={route} />;
  return (
    <ProtectedDispatcherRoute>
      {(claims) => <DispatcherPortal route={route} claims={claims} />}
    </ProtectedDispatcherRoute>
  );
}
