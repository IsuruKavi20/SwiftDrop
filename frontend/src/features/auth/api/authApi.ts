import { env } from '../../../config/env';
import { createChallenge, takeVerifier } from '../lib/pkce';
import { clearTokens, getAccessToken, getRefreshToken, saveTokens, type Tokens } from '../lib/session';

/** Errors from the auth service come as RFC 9457 Problem Details: show their "detail", never raw internals. */
export class AuthError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${env.authUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new AuthError(response.status, problem?.detail ?? 'Something went wrong. Please try again.', problem?.code);
  }
  return (response.status === 204 || response.status === 202 ? undefined : await response.json()) as T;
}

/** Always answers the same way whether or not the email is new (the service hides which emails exist). */
export function signUp(email: string, password: string): Promise<void> {
  return post<void>('/api/v1/auth/signup', { email, password });
}

export async function logIn(email: string, password: string): Promise<void> {
  saveTokens(await post<Tokens>('/api/v1/auth/login', { email, password }));
}

export function verifyEmail(token: string): Promise<void> {
  return post<void>('/api/v1/auth/email-verification', { token });
}

/** Leaves the page: Google, then the auth service, then back to /auth/callback#code=... */
export async function startGoogleLogin(): Promise<void> {
  const challenge = await createChallenge();
  window.location.assign(`${env.authUrl}/oauth2/authorization/google?code_challenge=${challenge}`);
}

export async function finishGoogleLogin(code: string): Promise<void> {
  const verifier = takeVerifier();
  if (!verifier) {
    throw new AuthError(400, 'This login was started in another tab or has expired. Please try again.');
  }
  saveTokens(await post<Tokens>('/api/v1/auth/google/exchange', { code, verifier }));
}

/** Swaps the refresh token for a new pair (the old refresh token stops working: rotation). */
export async function refresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  try {
    saveTokens(await post<Tokens>('/api/v1/auth/refresh', { refresh_token: refreshToken }));
    return true;
  } catch {
    clearTokens();
    return false;
  }
}

export async function logOut(): Promise<void> {
  const refreshToken = getRefreshToken();
  clearTokens();
  if (refreshToken) {
    await post<void>('/api/v1/auth/logout', { refresh_token: refreshToken }).catch(() => undefined);
  }
}

/** Calls a SwiftDrop API with the access token; on 401 it refreshes once and retries. */
export async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  if (!getAccessToken()) {
    await refresh();
  }
  const send = () =>
    fetch(url, { ...init, headers: { ...init.headers, Authorization: `Bearer ${getAccessToken() ?? ''}` } });
  let response = await send();
  if (response.status === 401 && (await refresh())) {
    response = await send();
  }
  return response;
}
