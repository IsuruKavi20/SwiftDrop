/**
 * The signed-in session.
 * - Access token (15 min): kept ONLY in memory, so it is gone when the tab closes and no script storage holds it.
 * - Refresh token (1 day, rotated on every use by the auth service): kept in sessionStorage so a page reload
 *   doesn't log the user out; it is cleared when the tab closes. Trade-off: a successful XSS attack could read it,
 *   which is why the auth service rotates it and detects reuse (a stolen copy ends the whole session family).
 */
export interface Tokens {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

export interface AccessClaims {
  sub: string;
  roles?: string[];
  entitlements?: string[];
  exp: number;
}

const REFRESH_KEY = 'swiftdrop.refresh';
let accessToken: string | null = null;

export function saveTokens(tokens: Tokens): void {
  accessToken = tokens.access_token;
  sessionStorage.setItem(REFRESH_KEY, tokens.refresh_token);
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY);
}

export function clearTokens(): void {
  accessToken = null;
  sessionStorage.removeItem(REFRESH_KEY);
}

/** Reads the token's claims for display only. Trust decisions are made by the servers, never by this code. */
export function readClaims(token: string): AccessClaims {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(payload)) as AccessClaims;
}
