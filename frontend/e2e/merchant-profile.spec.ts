import { test, expect, type Page, type Route } from '@playwright/test';

interface MockClaims {
  sub: string;
  name?: string;
  email?: string;
  roles?: string[];
  entitlements?: string[];
}

interface SimulatedMerchantProfile {
  id: number;
  authUserId: string;
  ownerName: string;
  businessName: string | null;
  businessEmail: string | null;
  contactNumber: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  province: string | null;
  postalCode: string | null;
  country: string | null;
  complete: boolean;
  createdAt: string;
  updatedAt: string;
}

function createJwt(claims: MockClaims): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  const payload = btoa(
    JSON.stringify({
      exp: Math.floor(Date.now() / 1000) + 7200,
      ...claims,
    })
  )
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${payload}.mock-sig`;
}

function initialEmptyProfile(ownerName = 'Nimal Perera'): SimulatedMerchantProfile {
  const now = new Date().toISOString();
  return {
    id: 101,
    authUserId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    ownerName,
    businessName: null,
    businessEmail: null,
    contactNumber: null,
    addressLine1: null,
    addressLine2: null,
    city: null,
    province: null,
    postalCode: null,
    country: null,
    complete: false,
    createdAt: now,
    updatedAt: now,
  };
}

async function setupNetworkMocks(
  page: Page,
  options: {
    claims?: MockClaims;
    initialProfile?: SimulatedMerchantProfile;
    getProfileStatus?: number;
    putProfileStatus?: number;
    putProblemDetails?: Record<string, unknown>;
    networkFailPut?: boolean;
    refreshFails?: boolean;
  } = {}
) {
  const claims = options.claims ?? {
    sub: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    name: 'Nimal Perera',
    email: 'nimal@example.com',
    roles: ['MERCHANT'],
    entitlements: ['merchant:api-access', 'parcel:create', 'parcel:read-own'],
  };

  const token = createJwt(claims);
  let currentProfile: SimulatedMerchantProfile =
    options.initialProfile ?? initialEmptyProfile(claims.name ?? 'Nimal Perera');

  // Intercept Auth Login
  await page.route('**/api/v1/auth/login', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        access_token: token,
        refresh_token: 'mock-refresh-token',
        expires_in: 900,
      }),
    });
  });

  // Intercept Auth Refresh
  await page.route('**/api/v1/auth/refresh', async (route: Route) => {
    if (options.refreshFails) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Session expired' }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        access_token: token,
        refresh_token: 'mock-refresh-token-rotated',
        expires_in: 900,
      }),
    });
  });

  // Intercept Auth Logout
  await page.route('**/api/v1/auth/logout', async (route: Route) => {
    await route.fulfill({ status: 204 });
  });

  // Intercept Merchant Profile API
  await page.route('**/api/merchants/me', async (route: Route) => {
    if (route.request().url().includes('/deliveries') || route.request().url().includes('/dashboard')) {
      return route.fallback();
    }
    const method = route.request().method();

    if (method === 'GET') {
      if (options.getProfileStatus && options.getProfileStatus !== 200) {
        await route.fulfill({
          status: options.getProfileStatus,
          contentType: 'application/json',
          body: JSON.stringify({
            detail: 'Access forbidden. Your account does not have merchant API permissions.',
          }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(currentProfile),
      });
      return;
    }

    if (method === 'PUT') {
      if (options.networkFailPut) {
        await route.abort('failed');
        return;
      }

      if (options.putProfileStatus && options.putProfileStatus !== 200) {
        await route.fulfill({
          status: options.putProfileStatus,
          contentType: 'application/json',
          body: JSON.stringify(
            options.putProblemDetails ?? {
              detail: 'Saving profile failed with status ' + options.putProfileStatus,
            }
          ),
        });
        return;
      }

      const postData = route.request().postDataJSON() as Record<string, string | null>;
      currentProfile = {
        ...currentProfile,
        businessName: postData.businessName ?? null,
        businessEmail: postData.businessEmail ?? null,
        contactNumber: postData.contactNumber ?? null,
        addressLine1: postData.addressLine1 ?? null,
        addressLine2: postData.addressLine2 ?? null,
        city: postData.city ?? null,
        province: postData.province ?? null,
        postalCode: postData.postalCode ?? null,
        country: postData.country ?? null,
        complete: Boolean(
          postData.businessName &&
            postData.businessEmail &&
            postData.contactNumber &&
            postData.addressLine1 &&
            postData.city &&
            postData.country
        ),
        updatedAt: new Date().toISOString(),
      };

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(currentProfile),
      });
      return;
    }

    await route.continue();
  });

  // Intercept Customer dashboard/profile in case navigation tests touch them
  await page.route('**/api/customer/profile', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: 1, name: claims.name, phoneNumber: '0771234567' }),
    });
  });

  await page.route('**/api/customer/dashboard', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalDeliveries: 0,
        activeDeliveries: 0,
        countsByStatus: { PENDING: 0, CONFIRMED: 0, DELIVERED: 0, CANCELLED: 0 },
        recentDeliveries: [],
      }),
    });
  });

  // Intercept Merchant dashboard and deliveries for landing navigation
  await page.route('**/api/merchants/me/dashboard', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        businessName: currentProfile.businessName,
        profileComplete: currentProfile.complete,
        totalOrders: 0,
        pendingOrders: 0,
        inTransitOrders: 0,
        deliveredOrders: 0,
        countsByStatus: { PENDING: 0, CONFIRMED: 0, DELIVERED: 0, CANCELLED: 0 },
        recentOrders: [],
      }),
    });
  });

  await page.route(/\/api\/merchants\/me\/deliveries(\?.*)?$/, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
    });
  });

  return {
    getCurrentProfile: () => currentProfile,
    setCurrentProfile: (p: SimulatedMerchantProfile) => {
      currentProfile = p;
    },
    token,
  };
}

async function injectSessionToken(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('swiftdrop.refresh', 'mock-initial-refresh');
  });
}

test.describe('Merchant Business Profile E2E', () => {
  test('1. Merchant login reaches /merchant/dashboard via landing router and navigates to profile', async ({ page }) => {
    await setupNetworkMocks(page);

    await page.goto('/login');
    await page.fill('#email', 'nimal@ceylontea.lk');
    await page.fill('#password', 'Secret123!');
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL(/\/merchant\/dashboard/);
    await page.click('button:has-text("Business Profile")');
    await expect(page).toHaveURL(/\/merchant\/profile/);
    await expect(page.locator('#profile-title')).toHaveText('Business Profile');
  });

  test('2. GET loads the initial profile with read-only owner name and incomplete status', async ({ page }) => {
    await setupNetworkMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await expect(page.locator('#profile-title')).toBeVisible();
    await expect(page.locator('#ownerName')).toHaveValue('Nimal Perera');
    await expect(page.locator('#ownerName')).toHaveAttribute('readonly', '');
    await expect(page.locator('text=Profile incomplete')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toHaveText('Complete Business Profile');
  });

  test('3. Client validation rejects empty and whitespace-only required fields', async ({ page }) => {
    await setupNetworkMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/profile');
    await expect(page.locator('#profile-title')).toBeVisible();

    // Type spaces into required fields
    await page.fill('#businessName', '    ');
    await page.fill('#businessEmail', 'invalid-email');
    await page.click('button[type="submit"]');

    await expect(page.locator('#err-businessName')).toHaveText('Business name is required.');
    await expect(page.locator('#err-businessEmail')).toHaveText('Please provide a valid email address.');
    await expect(page.locator('#err-contactNumber')).toHaveText('Contact phone number is required.');
    await expect(page.locator('role=alert')).toContainText('Please correct the highlighted fields before saving.');
  });

  test('4. PUT saves valid details and updates UI state to complete without reload', async ({ page }) => {
    await setupNetworkMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await page.fill('#businessName', 'Ceylon Tea House');
    await page.fill('#businessEmail', 'orders@ceylontea.lk');
    await page.fill('#contactNumber', '0112345678');
    await page.fill('#addressLine1', '12 Galle Road');
    await page.fill('#addressLine2', 'Suite 4B');
    await page.fill('#city', 'Colombo');
    await page.fill('#province', 'Western');
    await page.fill('#postalCode', '00300');
    await page.fill('#country', 'Sri Lanka');

    await page.click('button[type="submit"]');

    // Verify success banner and updated status badge
    await expect(page.locator('role=status >> text=Business profile updated successfully.')).toBeVisible();
    await expect(page.locator('text=Profile complete')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toHaveText('Update Business Profile');
  });

  test('5. Browser refresh reloads saved profile values from server', async ({ page }) => {
    const savedProfile: SimulatedMerchantProfile = {
      id: 202,
      authUserId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      ownerName: 'Nimal Perera',
      businessName: 'Ceylon Tea House',
      businessEmail: 'orders@ceylontea.lk',
      contactNumber: '0112345678',
      addressLine1: '12 Galle Road',
      addressLine2: 'Suite 4B',
      city: 'Colombo',
      province: 'Western',
      postalCode: '00300',
      country: 'Sri Lanka',
      complete: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    await setupNetworkMocks(page, { initialProfile: savedProfile });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await expect(page.locator('#businessName')).toHaveValue('Ceylon Tea House');
    await expect(page.locator('#contactNumber')).toHaveValue('0112345678');
    await expect(page.locator('text=Profile complete')).toBeVisible();

    // Reload page
    await page.reload();

    await expect(page.locator('#profile-title')).toBeVisible();
    await expect(page.locator('#businessName')).toHaveValue('Ceylon Tea House');
    await expect(page.locator('#businessEmail')).toHaveValue('orders@ceylontea.lk');
    await expect(page.locator('#contactNumber')).toHaveValue('0112345678');
    await expect(page.locator('#postalCode')).toHaveValue('00300');
    await expect(page.locator('text=Profile complete')).toBeVisible();
  });

  test('6. An existing completed profile can be edited and saved again', async ({ page }) => {
    const savedProfile: SimulatedMerchantProfile = {
      id: 303,
      authUserId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      ownerName: 'Nimal Perera',
      businessName: 'Original Brand',
      businessEmail: 'original@brand.lk',
      contactNumber: '0111111111',
      addressLine1: '10 First Ave',
      addressLine2: null,
      city: 'Colombo',
      province: 'Western',
      postalCode: '00100',
      country: 'Sri Lanka',
      complete: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    await setupNetworkMocks(page, { initialProfile: savedProfile });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');
    await expect(page.locator('#businessName')).toHaveValue('Original Brand');

    // Update details
    await page.fill('#businessName', 'Updated Global Brand');
    await page.fill('#contactNumber', '0119999999');
    await page.click('button[type="submit"]');

    await expect(page.locator('role=status >> text=Business profile updated successfully.')).toBeVisible();
    await expect(page.locator('#businessName')).toHaveValue('Updated Global Brand');
    await expect(page.locator('#contactNumber')).toHaveValue('0119999999');
  });

  test('7. Server validation errors (400) display inline and retain entered input', async ({ page }) => {
    await setupNetworkMocks(page, {
      putProfileStatus: 400,
      putProblemDetails: {
        title: 'Validation Failed',
        detail: 'Some fields are invalid. Please check your entries.',
        errors: {
          contactNumber: 'Contact number format is invalid.',
        },
      },
    });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await page.fill('#businessName', 'Tea Traders');
    await page.fill('#businessEmail', 'traders@tea.lk');
    await page.fill('#contactNumber', 'bad-phone');
    await page.fill('#addressLine1', '50 Port Road');
    await page.fill('#city', 'Colombo');
    await page.fill('#country', 'Sri Lanka');

    await page.click('button[type="submit"]');

    // Error message rendered
    await expect(page.locator('#err-contactNumber')).toHaveText('Contact number format is invalid.');
    await expect(page.locator('role=alert')).toContainText('Some fields are invalid. Please check your entries.');

    // Values retained
    await expect(page.locator('#businessName')).toHaveValue('Tea Traders');
    await expect(page.locator('#contactNumber')).toHaveValue('bad-phone');
    await expect(page.locator('button[type="submit"]')).toBeEnabled();
  });

  test('8. Network failure on save displays error, retains entered values, and enables retry', async ({ page }) => {
    await setupNetworkMocks(page, { networkFailPut: true });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await page.fill('#businessName', 'Reliable Goods');
    await page.fill('#businessEmail', 'info@reliable.lk');
    await page.fill('#contactNumber', '0115556677');
    await page.fill('#addressLine1', '88 High Street');
    await page.fill('#city', 'Galle');
    await page.fill('#country', 'Sri Lanka');

    await page.click('button[type="submit"]');

    // Error alert is shown
    await expect(page.locator('role=alert')).toContainText('Cannot reach the SwiftDrop server');

    // Inputs retained and button re-enabled for retry
    await expect(page.locator('#businessName')).toHaveValue('Reliable Goods');
    await expect(page.locator('#contactNumber')).toHaveValue('0115556677');
    await expect(page.locator('button[type="submit"]')).toBeEnabled();
  });

  test('9. 403 Forbidden on profile loading shows clean Access Denied state', async ({ page }) => {
    await setupNetworkMocks(page, { getProfileStatus: 403 });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');

    await expect(page.locator('h2:text("Access Denied")')).toBeVisible();
    await expect(page.locator('role=alert')).toContainText('Access forbidden. Your account does not have merchant API permissions.');
    await expect(page.locator('button:text("Go to Account")')).toBeVisible();
  });

  test('10. 401 Session Expiry on save redirects to login flow', async ({ page }) => {
    let putCalled = false;

    await setupNetworkMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/profile');
    await expect(page.locator('#profile-title')).toBeVisible();

    // Now re-route PUT and subsequent refresh to 401
    await page.route('**/api/merchants/me', async (route) => {
      if (route.request().method() === 'PUT') {
        putCalled = true;
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ detail: 'Token expired' }),
        });
        return;
      }
      await route.continue();
    });

    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Session expired' }),
      });
    });

    await page.fill('#businessName', 'Quick Drop');
    await page.fill('#businessEmail', 'qd@quick.lk');
    await page.fill('#contactNumber', '0112223344');
    await page.fill('#addressLine1', '10 Main Rd');
    await page.fill('#city', 'Colombo');
    await page.fill('#country', 'Sri Lanka');

    await page.click('button[type="submit"]');

    await expect(page).toHaveURL(/\/login/);
    expect(putCalled).toBe(true);
  });

  test('11. Customer-only users cannot access Merchant profile and see no Merchant navigation', async ({ page }) => {
    await setupNetworkMocks(page, {
      claims: {
        sub: 'cust-uuid-1234',
        name: 'Kasun Customer',
        roles: ['CUSTOMER'],
        entitlements: ['parcel:read-own'],
      },
    });
    await injectSessionToken(page);

    // Visiting /merchant/profile directly
    await page.goto('/merchant/profile');
    await expect(page.locator('h2:text("Access Denied")')).toBeVisible();
    await expect(page.locator('role=alert')).toContainText('You need merchant privileges to access this page.');

    // Visiting /customer/dashboard
    await page.goto('/customer/dashboard');
    await expect(page.locator('h1:text("Customer Dashboard")')).toBeVisible();
    await expect(page.locator('button:text("Merchant view")')).toHaveCount(0);
  });

  test('12. Users with MERCHANT role but missing merchant:api-access entitlement are blocked', async ({ page }) => {
    await setupNetworkMocks(page, {
      claims: {
        sub: 'unapproved-merchant-uuid',
        name: 'Pending Merchant',
        roles: ['MERCHANT'],
        entitlements: ['parcel:create'], // missing merchant:api-access
      },
    });
    await injectSessionToken(page);

    await page.goto('/merchant/profile');
    await expect(page.locator('h2:text("Access Denied")')).toBeVisible();
    await expect(page.locator('role=alert')).toContainText('You need merchant privileges to access this page.');
  });

  test('13. Multi-role users can switch between Merchant and Customer views seamlessly', async ({ page }) => {
    await setupNetworkMocks(page, {
      claims: {
        sub: 'dual-role-user-uuid',
        name: 'Chamara Dual',
        roles: ['MERCHANT', 'CUSTOMER'],
        entitlements: ['merchant:api-access', 'parcel:read-own', 'parcel:create'],
      },
    });
    await injectSessionToken(page);

    // On Merchant Profile: button to Customer Dashboard is visible
    await page.goto('/merchant/profile');
    await expect(page.locator('#profile-title')).toBeVisible();
    const customerDashBtn = page.locator('button:text("Customer Dashboard")');
    await expect(customerDashBtn).toBeVisible();

    // Click to switch to Customer Dashboard
    await customerDashBtn.click();
    await expect(page).toHaveURL(/\/customer\/dashboard/);

    // On Customer Dashboard: button to Merchant view is visible in sidebar footer
    const merchantViewBtn = page.locator('button:text("Merchant view")');
    await expect(merchantViewBtn).toBeVisible();

    // Customer → Merchant view reaches /merchant/dashboard
    await merchantViewBtn.click();
    await expect(page).toHaveURL(/\/merchant\/dashboard/);

    // The dashboard renders successfully
    await expect(page.locator('text=SwiftDrop Merchant')).toBeVisible();
    await expect(page.getByRole('region', { name: 'Order summary statistics' })).toBeVisible();

    // Its existing Business Profile navigation reaches /merchant/profile
    const businessProfileBtn = page.locator('button:text("Business Profile")');
    await expect(businessProfileBtn).toBeVisible();
    await businessProfileBtn.click();
    await expect(page).toHaveURL(/\/merchant\/profile/);

    // The profile form renders successfully
    await expect(page.locator('#profile-title')).toBeVisible();
    await expect(page.locator('#businessName')).toBeVisible();
  });

  test('14. Layout renders cleanly on mobile viewports', async ({ page }) => {
    await setupNetworkMocks(page);
    await injectSessionToken(page);

    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/merchant/profile');

    await expect(page.locator('#profile-title')).toBeVisible();
    await expect(page.locator('#businessName')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();

    // Verify inputs and submit button are within viewport
    const submitBox = await page.locator('button[type="submit"]').boundingBox();
    expect(submitBox).not.toBeNull();
    expect(submitBox!.width).toBeGreaterThan(150);
  });
});
