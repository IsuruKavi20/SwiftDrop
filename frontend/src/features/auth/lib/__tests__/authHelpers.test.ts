/**
 * Unit tests for the auth helpers (where a login lands, who sees which area, fresh roles).
 * Run: npx tsx --test src/features/auth/lib/__tests__/authHelpers.test.ts
 * Node has no browser, so the few browser parts these helpers use are small fakes set up first.
 */
import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';

class MemoryStorage {
  private items = new Map<string, string>();
  getItem(key: string) { return this.items.get(key) ?? null; }
  setItem(key: string, value: string) { this.items.set(key, String(value)); }
  removeItem(key: string) { this.items.delete(key); }
  clear() { this.items.clear(); }
}
const g = globalThis as Record<string, unknown>;
g.localStorage = new MemoryStorage();
g.sessionStorage = new MemoryStorage();
g.window = { location: { search: '', hash: '', pathname: '/' }, history: { replaceState() {} } };

const { routes } = await import('../../../../app/router');
const { rememberNextFromUrl, forgetNext, landingPage } = await import('../landing');
const { areasFor } = await import('../areas');
const { claimsWithFreshRoles } = await import('../freshClaims');
const { saveTokens, clearTokens, getAccessToken } = await import('../session');

/** An unsigned token with these permissions (the helpers only read it; servers check signatures). */
function token(entitlements: string[], expiresInSeconds = 900): string {
  const body = { sub: 'user-1', entitlements, exp: Math.floor(Date.now() / 1000) + expiresInSeconds };
  const part = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${part({ alg: 'none' })}.${part(body)}.x`;
}

function signIn(entitlements: string[], expiresInSeconds = 900): void {
  saveTokens({ access_token: token(entitlements, expiresInSeconds), refresh_token: 'refresh-1', expires_in: 900 });
}

function cameFrom(search: string): void {
  (g.window as { location: { search: string } }).location.search = search;
}

const CUSTOMER = ['parcel:create', 'parcel:read-own'];
const MERCHANT = ['merchant:api-access', 'parcel:create', 'parcel:read-own'];
const ADMIN = ['user:read', 'parcel:read-all'];

beforeEach(() => {
  (g.localStorage as MemoryStorage).clear();
  (g.sessionStorage as MemoryStorage).clear();
  clearTokens();
  cameFrom('');
});

// ---- landing: where a login goes ----

test('each role lands on its own area', () => {
  signIn(ADMIN);
  assert.equal(landingPage(), '/admin');
  signIn(MERCHANT);
  assert.equal(landingPage(), routes.merchantDashboard);
  signIn(CUSTOMER);
  assert.equal(landingPage(), routes.customerDashboard);
  signIn([]);
  assert.equal(landingPage(), routes.account);
});

test('a customer who clicked "Sell with SwiftDrop" reaches the merchant application, once', () => {
  cameFrom('?next=/merchant/apply');
  rememberNextFromUrl();
  signIn(CUSTOMER);

  assert.equal(landingPage(), routes.merchantApply);
  assert.equal(landingPage(), routes.customerDashboard);   // used once, then forgotten
});

test('the wish survives a new tab (the confirm-email link) because it is not per tab', () => {
  cameFrom('?next=/merchant/apply');
  rememberNextFromUrl();
  (g.sessionStorage as MemoryStorage).clear();   // a new tab has its own sessionStorage
  signIn(CUSTOMER);

  assert.equal(landingPage(), routes.merchantApply);
});

test('admins and merchants ignore an old "Sell with SwiftDrop" click', () => {
  cameFrom('?next=/merchant/apply');
  rememberNextFromUrl();
  signIn(ADMIN);
  assert.equal(landingPage(), '/admin');

  rememberNextFromUrl();
  signIn(MERCHANT);
  assert.equal(landingPage(), routes.merchantDashboard);
});

test('only allowed pages are remembered (no open redirect to another site)', () => {
  cameFrom('?next=https://evil.example/login');
  rememberNextFromUrl();
  signIn(CUSTOMER);

  assert.equal(landingPage(), routes.customerDashboard);
});

test('an expired or broken wish is ignored, and plain "Log in" forgets it', () => {
  localStorage.setItem('swiftdrop.next', JSON.stringify({ page: routes.merchantApply, until: Date.now() - 1 }));
  signIn(CUSTOMER);
  assert.equal(landingPage(), routes.customerDashboard);

  localStorage.setItem('swiftdrop.next', '{not json');
  assert.equal(landingPage(), routes.customerDashboard);

  cameFrom('?next=/merchant/apply');
  rememberNextFromUrl();
  forgetNext();
  assert.equal(landingPage(), routes.customerDashboard);
});

test('signed out, the login page is the answer and a wish is kept for the real login', () => {
  cameFrom('?next=/merchant/apply');
  rememberNextFromUrl();

  assert.equal(landingPage(), routes.login);
  signIn(CUSTOMER);
  assert.equal(landingPage(), routes.merchantApply);
});

// ---- areas: which "view" buttons a person may see ----

test('staff who are also customers get both areas; a merchant never gets the customer screens', () => {
  assert.deepEqual(areasFor([...ADMIN, ...CUSTOMER]).map((a) => a.id), ['admin', 'customer']);
  assert.deepEqual(areasFor(MERCHANT).map((a) => a.id), ['merchant']);
  assert.deepEqual(areasFor(CUSTOMER).map((a) => a.id), ['customer']);
  assert.deepEqual(areasFor([]), []);
});

// ---- fresh roles: a role given a moment ago works at once ----

test('a missing role triggers one refresh, and the new token is used', async () => {
  signIn(CUSTOMER);
  let calls = 0;
  g.fetch = async () => {
    calls++;
    return new Response(JSON.stringify({ access_token: token(MERCHANT), refresh_token: 'refresh-2', expires_in: 900 }),
      { status: 200, headers: { 'Content-Type': 'application/json' } });
  };

  const [a, b] = await Promise.all([
    claimsWithFreshRoles('merchant:api-access'),
    claimsWithFreshRoles('merchant:api-access'),
  ]);

  assert.equal(calls, 1);   // refresh tokens are single-use: two guards must share one refresh
  assert.ok(a?.entitlements?.includes('merchant:api-access'));
  assert.ok(b?.entitlements?.includes('merchant:api-access'));
});

test('a role the token already has needs no refresh', async () => {
  signIn(MERCHANT);
  g.fetch = async () => { throw new Error('must not be called'); };

  const claims = await claimsWithFreshRoles('merchant:api-access');

  assert.ok(claims?.entitlements?.includes('merchant:api-access'));
});

test('when the refresh is refused, a signed-out person gets null', async () => {
  g.fetch = async () => new Response('{}', { status: 401 });

  assert.equal(await claimsWithFreshRoles('user:read'), null);
  assert.equal(getAccessToken(), null);
});
