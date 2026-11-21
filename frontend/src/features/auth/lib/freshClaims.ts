import { refresh } from '../api/authApi';
import { getAccessToken, readClaims, type AccessClaims } from './session';

/**
 * Claims for page guards, with "check again before saying no".
 * Roles live inside the access token (15 min). When an admin grants a role, the old token doesn't have it yet,
 * but a refresh asks the auth service, which builds the new token from the CURRENT roles. So when the needed
 * entitlement is missing we refresh once and look again: a new role works straight away, no logout.
 * The servers still check every call; this only decides which page to show.
 */
let pendingRefresh: Promise<boolean> | undefined;

function currentClaims(): AccessClaims | null {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const claims = readClaims(token);
    return claims.exp > Date.now() / 1000 ? claims : null;
  } catch {
    return null;
  }
}

/** One refresh at a time, shared by every guard: the refresh token is single-use. */
function refreshOnce(): Promise<boolean> {
  pendingRefresh ??= refresh().catch(() => false).finally(() => {
    pendingRefresh = undefined;
  });
  return pendingRefresh;
}

/** null = not signed in. Otherwise the claims, refreshed once if `entitlement` was missing. */
export async function claimsWithFreshRoles(entitlement: string): Promise<AccessClaims | null> {
  const claims = currentClaims();
  if (claims?.entitlements?.includes(entitlement)) return claims;
  const refreshed = await refreshOnce();
  if (!refreshed) return claims; // keep the old (still valid) claims, or null when signed out
  return currentClaims();
}
