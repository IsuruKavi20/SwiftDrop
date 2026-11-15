/** Fired after an assignment (or other admin change), so the sidebar counts and open lists reload. */
export const DISPATCH_CHANGED_EVENT = 'swiftdrop:dispatch-changed';

export class DispatchError extends Error {}

/**
 * Where the dispatch screens get their data: the backend for signed-in admins, the browser's sample data for the
 * development preview (which has no sign-in). DispatcherArea sets it from the route.
 */
let source: 'api' | 'sample' = 'api';

export function setDispatchSource(next: 'api' | 'sample') {
  source = next;
}

export function usingSampleData(): boolean {
  return source === 'sample';
}
