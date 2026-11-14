import { test, expect, type Page, type Route } from '@playwright/test';

interface MockClaims {
  sub: string;
  name?: string;
  email?: string;
  roles?: string[];
  entitlements?: string[];
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

const SAMPLE_ORDERS = [
  {
    id: 101,
    trackingNumber: 'SD-TEA001',
    deliveryType: 'EXPRESS',
    status: 'PENDING',
    receiverName: 'Kamal Perera',
    destinationCity: 'Kandy',
    estimatedDeliveryDate: '2026-11-20',
    createdAt: '2026-11-18T10:30:00Z',
  },
  {
    id: 102,
    trackingNumber: 'SD-TEA002',
    deliveryType: 'STANDARD',
    status: 'IN_TRANSIT',
    receiverName: 'Anura Silva',
    destinationCity: 'Galle',
    estimatedDeliveryDate: '2026-11-22',
    createdAt: '2026-11-17T09:15:00Z',
  },
  {
    id: 103,
    trackingNumber: 'SD-TEA003',
    deliveryType: 'SAME_DAY',
    status: 'DELIVERED',
    receiverName: 'Mala Fernando',
    destinationCity: 'Colombo',
    estimatedDeliveryDate: '2026-11-16',
    createdAt: '2026-11-16T08:00:00Z',
  },
];

const SAMPLE_DETAIL_101 = {
  id: 101,
  trackingNumber: 'SD-TEA001',
  publicTrackingToken: 'token-tea-001-xyz',
  deliveryType: 'EXPRESS',
  status: 'PENDING',
  specialInstructions: 'Handle fragile packaging with care',
  estimatedDeliveryDate: '2026-11-20',
  createdAt: '2026-11-18T10:30:00Z',
  deliveredAt: null,
  pickup: {
    contactName: 'Sunil Shantha',
    contactPhone: '0112345678',
    street: '12 Flower Road',
    city: 'Colombo',
    district: 'Colombo',
    postalCode: '00700',
  },
  destination: {
    contactName: 'Kamal Perera',
    contactPhone: '0812233445',
    street: '45 Peradeniya Road',
    city: 'Kandy',
    district: 'Kandy',
    postalCode: '20000',
  },
  parcels: [
    {
      id: 1,
      description: 'Organic Ceylon Black Tea (500g)',
      weightKg: 0.65,
      lengthCm: 15.0,
      widthCm: 10.0,
      heightCm: 8.0,
      declaredValue: 2500.0,
    },
  ],
  trackingEvents: [
    {
      id: 1,
      status: 'PENDING',
      description: 'Delivery booked by merchant',
      locationName: null,
      eventTime: '2026-11-18T10:30:00Z',
      recordedByName: 'Sunil Shantha',
    },
  ],
};

async function setupDashboardMocks(
  page: Page,
  options: {
    claims?: MockClaims;
    profileComplete?: boolean;
    businessName?: string;
    dashboardStatus?: number;
    deliveriesStatus?: number;
  } = {}
) {
  const claims = options.claims ?? {
    sub: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    name: 'Nimal Perera',
    email: 'nimal@ceylontea.lk',
    roles: ['MERCHANT'],
    entitlements: ['merchant:api-access', 'parcel:create', 'parcel:read-own'],
  };

  const token = createJwt(claims);
  const isComplete = options.profileComplete ?? true;
  const businessName = options.businessName ?? 'Ceylon Tea Exports';

  // Auth Mocks
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

  await page.route('**/api/v1/auth/refresh', async (route: Route) => {
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

  await page.route('**/api/v1/auth/logout', async (route: Route) => {
    await route.fulfill({ status: 204 });
  });

  // Merchant Profile API
  await page.route('**/api/merchants/me', async (route: Route) => {
    if (route.request().url().includes('/deliveries') || route.request().url().includes('/dashboard')) {
      return route.fallback();
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        complete: isComplete,
        ownerName: 'Nimal Perera',
        businessName,
        businessEmail: 'orders@ceylontea.lk',
        contactNumber: '0112345678',
        addressLine1: '12 Flower Road',
        city: 'Colombo',
        country: 'Sri Lanka',
        createdAt: '2026-11-10T00:00:00Z',
        updatedAt: '2026-11-10T00:00:00Z',
      }),
    });
  });

  // Merchant Dashboard API
  await page.route('**/api/merchants/me/dashboard', async (route: Route) => {
    if (options.dashboardStatus && options.dashboardStatus !== 200) {
      await route.fulfill({
        status: options.dashboardStatus,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Failed to retrieve dashboard stats' }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        businessName,
        profileComplete: isComplete,
        totalOrders: 3,
        pendingOrders: 1,
        inTransitOrders: 1,
        deliveredOrders: 1,
        countsByStatus: {
          PENDING: 1,
          CONFIRMED: 0,
          PICKED_UP: 0,
          IN_TRANSIT: 1,
          OUT_FOR_DELIVERY: 0,
          DELIVERED: 1,
          FAILED: 0,
          CANCELLED: 0,
        },
        recentOrders: SAMPLE_ORDERS,
      }),
    });
  });

  // Merchant Deliveries API
  await page.route(/\/api\/merchants\/me\/deliveries(\?.*)?$/, async (route: Route) => {
    if (route.request().method() === 'GET') {
      if (options.deliveriesStatus && options.deliveriesStatus !== 200) {
        await route.fulfill({
          status: options.deliveriesStatus,
          contentType: 'application/json',
          body: JSON.stringify({ detail: 'Failed to load deliveries' }),
        });
        return;
      }

      const url = new URL(route.request().url());
      const statusParam = url.searchParams.get('status');
      const pageParam = parseInt(url.searchParams.get('page') || '0', 10);

      let content = SAMPLE_ORDERS;
      if (statusParam) {
        content = SAMPLE_ORDERS.filter((o) => o.status === statusParam);
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content,
          page: pageParam,
          size: 10,
          totalElements: content.length,
          totalPages: 1,
        }),
      });
      return;
    }

    await route.fallback();
  });

  // Merchant Delivery Detail API
  await page.route(/\/api\/merchants\/me\/deliveries\/\d+/, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAMPLE_DETAIL_101),
    });
  });
}

async function injectSessionToken(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('swiftdrop.refresh', 'mock-initial-refresh');
  });
}

test.describe('Merchant Dashboard E2E', () => {
  test('1. Dashboard renders stat cards, trading name, and orders table', async ({ page }) => {
    await setupDashboardMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');

    // Navbar identity
    await expect(page.locator('text=SwiftDrop Merchant')).toBeVisible();
    await expect(page.locator('text=• Ceylon Tea Exports')).toBeVisible();

    // Stats
    const statsRegion = page.getByRole('region', { name: 'Order summary statistics' });
    await expect(statsRegion.getByText('Total Orders', { exact: true })).toBeVisible();
    await expect(statsRegion.getByText('Pending', { exact: true })).toBeVisible();
    await expect(statsRegion.getByText('In Transit', { exact: true })).toBeVisible();
    await expect(statsRegion.getByText('Delivered', { exact: true })).toBeVisible();

    // Orders table
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=SD-TEA002')).toBeVisible();
    await expect(page.locator('text=SD-TEA003')).toBeVisible();
    await expect(page.locator('text=Kamal Perera')).toBeVisible();
    await expect(page.locator('text=Kandy')).toBeVisible();
  });

  test('2. Displays incomplete profile warning banner when profile is incomplete', async ({ page }) => {
    await setupDashboardMocks(page, { profileComplete: false });
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');

    const banner = page.locator('text=Complete your merchant business profile');
    await expect(banner).toBeVisible();

    // Clicking "Complete Profile →" button navigates to /merchant/profile
    await page.click('button:has-text("Complete Profile →")');
    await expect(page).toHaveURL(/\/merchant\/profile/);
  });

  test('3. Status filter pills filter orders table', async ({ page }) => {
    await setupDashboardMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=SD-TEA002')).toBeVisible();

    // Click "Pending" filter
    await page.click('button:has-text("Pending")');

    // Only pending order should remain
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=SD-TEA002')).not.toBeVisible();
    await expect(page.locator('text=SD-TEA003')).not.toBeVisible();

    // Click "All" filter to restore
    await page.click('button:has-text("All")');
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=SD-TEA002')).toBeVisible();
  });

  test('4. Clicking an order row opens details modal with addresses, parcels, and timeline', async ({ page }) => {
    await setupDashboardMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');

    // Click order row or View button for SD-TEA001
    await page.click('text=SD-TEA001');

    // Modal opens
    const modal = page.locator('role=dialog');
    await expect(modal).toBeVisible();
    await expect(modal.locator('#modal-order-title')).toHaveText('SD-TEA001');

    // Addresses
    await expect(modal.locator('text=12 Flower Road')).toBeVisible();
    await expect(modal.locator('text=45 Peradeniya Road')).toBeVisible();
    await expect(modal.getByText('Sunil Shantha', { exact: true })).toBeVisible();
    await expect(modal.getByText('Kamal Perera', { exact: true })).toBeVisible();

    // Parcels
    await expect(modal.locator('text=Organic Ceylon Black Tea (500g)')).toBeVisible();
    await expect(modal.locator('text=15 × 10 × 8 cm')).toBeVisible();
    await expect(modal.locator('text=0.65 kg')).toBeVisible();
    await expect(modal.locator('text=Valued at LKR 2500.00')).toBeVisible();

    // Special instructions
    await expect(modal.locator('text=Handle fragile packaging with care')).toBeVisible();

    // Timeline
    await expect(modal.locator('text=Delivery booked by merchant')).toBeVisible();
    await expect(modal.locator('text=Recorded by Sunil Shantha')).toBeVisible();
  });

  test('5. Dismisses details modal via close button and Escape key', async ({ page }) => {
    await setupDashboardMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');
    await page.click('text=SD-TEA001');

    const modal = page.locator('role=dialog');
    await expect(modal).toBeVisible();

    // Close via close button
    await modal.locator('button[aria-label="Close details"]').click();
    await expect(modal).not.toBeVisible();

    // Reopen and close via Escape key
    await page.click('text=SD-TEA001');
    await expect(modal).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(modal).not.toBeVisible();
  });

  test('6. Error state shows retry button and recovers on retry', async ({ page }) => {
    let failFirst = true;
    const claims = {
      sub: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
      name: 'Nimal Perera',
      email: 'nimal@ceylontea.lk',
      roles: ['MERCHANT'],
      entitlements: ['merchant:api-access'],
    };
    const token = createJwt(claims);

    await page.route('**/api/v1/auth/refresh', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ access_token: token, refresh_token: 'mock-refresh', expires_in: 900 }),
      });
    });

    await page.route('**/api/merchants/me/dashboard', async (route: Route) => {
      if (failFirst) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ detail: 'Internal server error' }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            businessName: 'Recovered Tea House',
            profileComplete: true,
            totalOrders: 0,
            pendingOrders: 0,
            inTransitOrders: 0,
            deliveredOrders: 0,
            countsByStatus: { PENDING: 0, CONFIRMED: 0, DELIVERED: 0, CANCELLED: 0 },
            recentOrders: [],
          }),
        });
      }
    });

    await page.route(/\/api\/merchants\/me\/deliveries(\?.*)?$/, async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
      });
    });

    await injectSessionToken(page);
    await page.goto('/merchant/dashboard');

    await expect(page.locator('text=Error Loading Dashboard')).toBeVisible();

    // Now allow next request to succeed and click Retry
    failFirst = false;
    await page.click('button:has-text("Retry")');

    await expect(page.locator('text=Recovered Tea House')).toBeVisible();
  });

  test('7. Access denied for user without merchant:api-access entitlement', async ({ page }) => {
    await setupDashboardMocks(page, {
      claims: {
        sub: 'customer-user-id',
        name: 'Regular Customer',
        email: 'customer@example.com',
        roles: ['CUSTOMER'],
        entitlements: ['parcel:read-own', 'parcel:create'],
      },
    });
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');

    await expect(page.locator('text=Access Denied')).toBeVisible();
    await expect(
      page.locator('text=You need merchant privileges to access this dashboard')
    ).toBeVisible();
  });

  test('8. Mobile viewport adapts layout cleanly', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await setupDashboardMocks(page);
    await injectSessionToken(page);

    await page.goto('/merchant/dashboard');

    await expect(page.locator('text=SwiftDrop Merchant')).toBeVisible();
    const statsRegion = page.getByRole('region', { name: 'Order summary statistics' });
    await expect(statsRegion.getByText('Total Orders', { exact: true })).toBeVisible();
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
  });
});
