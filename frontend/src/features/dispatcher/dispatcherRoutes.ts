import type { DispatcherIconName } from './components/DispatcherIcon';

/**
 * Every admin screen, in sidebar order. Admins also do the dispatch work (there is no separate dispatcher role),
 * so both groups share one sidebar.
 */
export const dispatcherPages = [
  { id: 'admin', title: 'Overview', icon: 'grid', section: 'Admin' },
  { id: 'users', title: 'Users and Roles', icon: 'users', section: 'Admin' },
  { id: 'merchants', title: 'Merchant Approvals', icon: 'store', section: 'Admin' },
  { id: 'couriers', title: 'Couriers', icon: 'truck', section: 'Admin' },
  { id: 'zones', title: 'Delivery Zones', icon: 'map', section: 'Admin' },
  { id: 'pricing', title: 'Pricing', icon: 'tag', section: 'Admin' },
  { id: 'reports', title: 'Reports', icon: 'chart', section: 'Admin' },
  { id: 'operations', title: 'Operations Overview', icon: 'pulse', section: 'Dispatch' },
  { id: 'unassigned', title: 'Unassigned Parcels', icon: 'inbox', section: 'Dispatch' },
  { id: 'deliveries', title: 'All Deliveries', icon: 'box', section: 'Dispatch' },
  { id: 'workloads', title: 'Courier Workloads', icon: 'assign', section: 'Dispatch' },
  { id: 'failed', title: 'Failed Deliveries', icon: 'alert', section: 'Dispatch' },
] as const satisfies readonly { id: string; title: string; icon: DispatcherIconName; section: 'Admin' | 'Dispatch' }[];

export type DispatcherPage = (typeof dispatcherPages)[number];
export type DispatcherPageId = DispatcherPage['id'];
export const dispatcherSections = ['Admin', 'Dispatch'] as const;

export interface DispatcherRoute {
  page: DispatcherPage;
  /** Development only: shows the screens without signing in (like /merchant/preview). */
  preview: boolean;
}

const BASE = '/admin';
const PREVIEW_BASE = '/admin/preview';

export function dispatcherPath(id: DispatcherPageId, preview: boolean): string {
  const base = preview ? PREVIEW_BASE : BASE;
  return id === 'admin' ? base : `${base}/${id}`;
}

/** /admin, /admin/{page}; in development also /admin/preview and /admin/preview/{page}. */
export function matchDispatcherRoute(path: string): DispatcherRoute | undefined {
  const clean = path.length > 1 ? path.replace(/\/+$/, '') : path;
  for (const page of dispatcherPages) {
    if (clean === dispatcherPath(page.id, false)) return { page, preview: false };
    if (import.meta.env.DEV && clean === dispatcherPath(page.id, true)) return { page, preview: true };
  }
  return undefined;
}
