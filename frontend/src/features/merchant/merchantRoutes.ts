import { routes } from '../../app/router';
import type { MerchantIconName } from './components/MerchantIcon';

export const merchantPages = [
  { id: 'dashboard', title: 'Dashboard', icon: 'home' },
  { id: 'orders', title: 'Store Orders', icon: 'box' },
  { id: 'create-order', title: 'Create Order', icon: 'plus' },
  { id: 'business-profile', title: 'Business Profile', icon: 'cart' },
  { id: 'tracking', title: 'Tracking', icon: 'pin' },
  { id: 'api-credentials', title: 'API Credentials', icon: 'key' },
  { id: 'webhooks', title: 'Webhooks', icon: 'bolt' },
  { id: 'settings', title: 'Settings', icon: 'settings' },
] as const satisfies readonly { id: string; title: string; icon: MerchantIconName }[];

export type MerchantPageId = (typeof merchantPages)[number]['id'];

export function merchantPath(id: MerchantPageId, preview: boolean): string {
  if (preview) return id === 'dashboard' ? routes.merchantPreview : `${routes.merchantPreview}/${id}`;
  return id === 'dashboard' ? routes.merchantDashboard : `/merchant/${id}`;
}

export function matchMerchantRoute(path: string) {
  for (const page of merchantPages) {
    if (path === merchantPath(page.id, false)) return { page, preview: false };
    if (import.meta.env.DEV && path === merchantPath(page.id, true)) return { page, preview: true };
    if (page.id === 'orders') {
      const detail = /^\/merchant\/(preview\/)?orders\/([1-9][0-9]*)$/.exec(path);
      if (detail && (!detail[1] || import.meta.env.DEV)) {
        return { page, preview: Boolean(detail[1]), orderId: detail[2] };
      }
    }
  }
}
