/**
 * The browser secret for Google login (login-CSRF protection of the auth service): a random "verifier" stays in
 * this tab, only its SHA-256 fingerprint ("challenge") is sent. After Google, only the tab holding the verifier
 * can exchange the one-time code for tokens.
 */
const VERIFIER_KEY = 'swiftdrop.google.verifier';

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function createChallenge(): Promise<string> {
  const verifier = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/** Returns the verifier once and forgets it (a code can only be exchanged once anyway). */
export function takeVerifier(): string | null {
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  sessionStorage.removeItem(VERIFIER_KEY);
  return verifier;
}
