import { useEffect, useState } from 'react';

/**
 * A tiny router for the few auth screens (no extra dependency). When the app grows, swap it for react-router;
 * the pages only use navigate() and the path.
 */
export const routes = {
  login: '/login',
  signup: '/signup',
  googleCallback: '/auth/callback',
  verifyEmail: '/verify-email',
  account: '/account',
  customerDashboard: '/customer/dashboard',
  customerNotifications: '/customer/notifications',
  merchantDashboard: '/merchant/dashboard',
  merchantProfile: '/merchant/profile',
  merchantPreview: '/merchant/preview',
  customerBook: '/customer/deliveries/new',
  customerDeliveries: '/customer/deliveries',
  customerTrack: '/customer/track',
  customerAccount: '/customer/profile',
  customerSupport: '/customer/support',
  publicTrack: '/track',
  merchantApply: '/merchant/apply',
} as const;

export function navigate(path: string, { replace = false } = {}): void {
  if (replace) {
    window.history.replaceState(null, '', path);
  } else {
    window.history.pushState(null, '', path);
  }
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function usePath(): string {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onChange = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onChange);
    return () => window.removeEventListener('popstate', onChange);
  }, []);
  return path;
}
