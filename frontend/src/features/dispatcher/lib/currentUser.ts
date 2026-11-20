import { getAccessToken, readClaims } from '../../auth/lib/session';

/**
 * The signed-in admin's account id (the token's subject), or undefined in the preview / before sign-in.
 * Only for warnings such as "this is your own account": the auth service still enforces its rules.
 */
export function currentUserId(): string | undefined {
  const token = getAccessToken();
  if (!token) return undefined;
  try {
    return readClaims(token).sub;
  } catch {
    return undefined;
  }
}
