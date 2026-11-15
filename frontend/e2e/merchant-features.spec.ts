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

async function injectMerchantSession(page: Page, claims?: MockClaims) {
  const merchantClaims: MockClaims = claims ?? {
    sub: 'c1111111-2222-3333-4444-555555555555',
    name: 'Dilhan Fernando',
    email: 'merchant@ceylontea.lk',
    roles: ['MERCHANT'],
    entitlements: ['merchant:api-access', 'parcel:create', 'parcel:read-own'],
  };

  const token = createJwt(merchantClaims);

  // Set sessionStorage token for SwiftDrop session restoration
  await page.addInitScript(() => {
    sessionStorage.setItem('swiftdrop.refresh', 'mock-initial-refresh');
  });

  // Intercept Auth Refresh API
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

  // Intercept Auth Logout API
  await page.route('**/api/v1/auth/logout', async (route: Route) => {
    await route.fulfill({ status: 204 });
  });
}

const MOCK_ORDER_DETAIL = {
  id: 101,
  trackingNumber: 'SD-TEA001',
  publicTrackingToken: 'pub-tok-101',
  deliveryType: 'EXPRESS',
  status: 'IN_TRANSIT',
  specialInstructions: 'Handle with care - fragile tea tin',
  estimatedDeliveryDate: '2026-11-25',
  createdAt: '2026-11-18T10:30:00Z',
  pickup: {
    contactName: 'Sunil Shantha',
    contactPhone: '+94112345678',
    street: '12 Flower Road',
    city: 'Colombo',
    district: 'Colombo',
    postalCode: '00700',
  },
  destination: {
    contactName: 'Kamal Perera',
    contactPhone: '+94812233445',
    street: '45 Peradeniya Road',
    city: 'Kandy',
    district: 'Kandy',
    postalCode: '20000',
  },
  parcels: [
    {
      id: 1,
      description: 'Ceylon Black Tea Tin',
      weightKg: 0.75,
      lengthCm: 20,
      widthCm: 15,
      heightCm: 10,
      declaredValue: 3500.0,
    },
  ],
  trackingEvents: [
    {
      id: 1,
      status: 'CONFIRMED',
      description: 'Order confirmed and registered',
      locationName: 'Colombo Central Hub',
      eventTime: '2026-11-18T10:30:00Z',
      recordedByName: 'Sunil Shantha',
    },
    {
      id: 2,
      status: 'IN_TRANSIT',
      description: 'Dispatched on route to Kandy Hub',
      locationName: 'Colombo Distribution Center',
      eventTime: '2026-11-18T14:15:00Z',
      recordedByName: 'Nimal Silva',
    },
  ],
};

test.describe('Merchant Module Full Feature Set', () => {
  test.beforeEach(async ({ page }) => {
    await injectMerchantSession(page);
  });

  test('1. Create Order: valid shipment submission succeeds and shows confirmation', async ({ page }) => {
    // Mock delivery creation
    await page.route('**/api/merchants/me/deliveries*', async (route: Route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_ORDER_DETAIL),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/merchant/create-order');

    await expect(page.locator('#create-order-title')).toHaveText('Book a Delivery Shipment');

    // Fill form
    await page.selectOption('select[name="deliveryType"]', 'EXPRESS');

    // Destination
    await page.fill('input[name="destination.contactName"]', 'Kamal Perera');
    await page.fill('input[name="destination.contactPhone"]', '+94812233445');
    await page.fill('input[name="destination.street"]', '45 Peradeniya Road');
    await page.fill('input[name="destination.city"]', 'Kandy');
    await page.fill('input[name="destination.district"]', 'Kandy');

    // Pickup
    await page.fill('input[name="pickup.contactName"]', 'Sunil Shantha');
    await page.fill('input[name="pickup.contactPhone"]', '+94112345678');
    await page.fill('input[name="pickup.street"]', '12 Flower Road');
    await page.fill('input[name="pickup.city"]', 'Colombo');
    await page.fill('input[name="pickup.district"]', 'Colombo');

    // Parcel
    await page.fill('input[name="parcel-1.description"]', 'Ceylon Black Tea Tin');
    await page.fill('input[name="parcel-1.weightKg"]', '0.75');
    await page.fill('input[name="parcel-1.lengthCm"]', '20');
    await page.fill('input[name="parcel-1.widthCm"]', '15');
    await page.fill('input[name="parcel-1.heightCm"]', '10');

    // Submit
    await page.click('button:has-text("Submit order")');

    // Confirmation card
    await expect(page.locator('text=Shipment Booked Successfully!')).toBeVisible();
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=View Order Details')).toBeVisible();
  });

  test('2. Bulk CSV Import: switches tab, uploads CSV and completes batch booking', async ({ page }) => {
    await page.route('**/api/merchants/me/deliveries*', async (route: Route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_ORDER_DETAIL),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/merchant/create-order');

    await page.click('button[role="tab"]:has-text("Bulk CSV Import")');
    await expect(page.locator('text=Bulk Shipment CSV Import')).toBeVisible();
    await expect(page.locator('button:has-text("Download CSV Template")')).toBeVisible();

    const csvContent = [
      'deliveryType,pickupContactName,pickupPhone,pickupStreet,pickupCity,pickupDistrict,pickupPostalCode,destContactName,destPhone,destStreet,destCity,destDistrict,destPostalCode,parcelDescription,weightKg,lengthCm,widthCm,heightCm,declaredValue,specialInstructions',
      'EXPRESS,Central Hub,+94112345678,12 Flower Rd,Colombo,Colombo,00700,Kamal Perera,+94812233445,45 Peradeniya Rd,Kandy,Kandy,20000,Ceylon Black Tea Tin,0.75,20,15,10,3500.00,Fragile',
    ].join('\n');

    await page.setInputFiles('input[type="file"]', {
      name: 'bulk-orders.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    await expect(page.locator('text=Import Preview')).toBeVisible();
    await expect(page.getByText('1 valid', { exact: true })).toBeVisible();
    await expect(page.locator('button:has-text("Start Import (1 valid)")')).toBeEnabled();

    await page.click('button:has-text("Start Import")');

    await expect(page.locator('text=Import Complete')).toBeVisible();
    await expect(page.locator('text=Successfully created: 1')).toBeVisible();
    await expect(page.locator('strong:has-text("SD-TEA001")')).toBeVisible();
  });

  test('3. Store Orders: lists orders with status filters and pagination', async ({ page }) => {
    let requestedPage = 0;
    let requestedStatus: string | null = null;

    await page.route('**/api/merchants/me/deliveries*', async (route: Route) => {
      const url = new URL(route.request().url());
      requestedStatus = url.searchParams.get('status');
      requestedPage = parseInt(url.searchParams.get('page') || '0', 10);

      let content = [
        {
          id: 101,
          trackingNumber: 'SD-TEA001',
          deliveryType: 'EXPRESS',
          status: 'IN_TRANSIT',
          receiverName: 'Kamal Perera',
          destinationCity: 'Kandy',
          createdAt: '2026-11-18T10:30:00Z',
        },
        {
          id: 102,
          trackingNumber: 'SD-TEA002',
          deliveryType: 'STANDARD',
          status: 'DELIVERED',
          receiverName: 'Anura Silva',
          destinationCity: 'Galle',
          createdAt: '2026-11-17T09:15:00Z',
        },
      ];

      if (requestedStatus && requestedStatus !== 'ALL') {
        content = content.filter((o) => o.status === requestedStatus);
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content,
          page: requestedPage,
          size: 10,
          totalElements: 15,
          totalPages: 2,
        }),
      });
    });

    await page.goto('/merchant/orders');
    await expect(page.locator('#orders-title')).toHaveText('Store Order History');
    await expect(page.locator('text=SD-TEA001')).toBeVisible();
    await expect(page.locator('text=Kamal Perera')).toBeVisible();
    await expect(page.locator('text=View Details').first()).toBeVisible();

    // Status filter
    await page.click('button:has-text("In Transit")');
    await expect(page.locator('text=SD-TEA001')).toBeVisible();

    // Pagination controls
    await expect(page.locator('text=Page 1 of 2')).toBeVisible();
    await page.click('button:has-text("Next →")');
    await expect(page.locator('text=Page 2 of 2')).toBeVisible();
  });

  test('4. Dedicated Order Details Page: displays pickup, destination, and tracking history', async ({ page }) => {
    await page.route('**/api/merchants/me/deliveries/101', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_ORDER_DETAIL),
      });
    });

    await page.goto('/merchant/orders/101');
    await expect(page.locator('#order-details-title')).toHaveText('SD-TEA001');
    await expect(page.locator('li', { hasText: 'Contact:' })).toContainText('Sunil Shantha');
    await expect(page.locator('text=12 Flower Road')).toBeVisible();
    await expect(page.locator('li', { hasText: 'Recipient:' })).toContainText('Kamal Perera');
    await expect(page.locator('text=45 Peradeniya Road')).toBeVisible();
    await expect(page.locator('text=Ceylon Black Tea Tin')).toBeVisible();
    await expect(page.locator('text=By: Sunil Shantha')).toBeVisible();
    await expect(page.locator('text=Colombo Central Hub')).toBeVisible();
    await expect(page.locator('text=Back to Store Orders')).toBeVisible();
  });

  test('5. API Credentials: generates client key and reveals secret once', async ({ page }) => {
    let clientsList = [
      {
        clientId: 'client_store_01',
        name: 'WooCommerce Store',
        createdAt: '2026-11-01T00:00:00Z',
      },
    ];

    await page.route(/\/api\/v1\/merchants\/me\/clients/, async (route: Route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(clientsList),
        });
      } else if (method === 'POST') {
        const postData = JSON.parse(route.request().postData() || '{}');
        const newClient = {
          clientId: 'client_new_888',
          name: postData.name || 'Mobile App',
          clientSecret: 'sec_test_secret_key_once_only_9999',
          createdAt: '2026-11-18T10:00:00Z',
        };
        clientsList = [newClient, ...clientsList];
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newClient),
        });
      } else if (method === 'DELETE') {
        clientsList = clientsList.filter((c) => !route.request().url().includes(c.clientId));
        await route.fulfill({ status: 204 });
      } else {
        await route.continue();
      }
    });

    await page.goto('/merchant/api-credentials');
    await expect(page.locator('#api-credentials-title')).toHaveText('API Credentials');
    await expect(page.locator('text=WooCommerce Store')).toBeVisible();

    // Open modal & create
    await page.click('button:has-text("Generate New API Key")');
    await page.fill('input[placeholder*="WooCommerce Store"]', 'Mobile App');
    await page.click('button:has-text("Generate Key")');

    // Verify secret card
    await expect(page.locator('text=Save Your Client Secret Now')).toBeVisible();
    await expect(page.locator('text=sec_test_secret_key_once_only_9999')).toBeVisible();
    await expect(page.locator('button:has-text("Copy Secret")')).toBeVisible();

    // Close secret card
    await page.click('button:has-text("I Have Saved My Secret")');
    await expect(page.locator('text=Save Your Client Secret Now')).not.toBeVisible();

    // Test revocation
    page.once('dialog', (dialog) => dialog.accept());
    await page.click('tr:has-text("client_store_01") button:has-text("Revoke")');
    await expect(page.locator('text=WooCommerce Store')).not.toBeVisible();
  });

  test('6. Webhooks: lists configured webhooks, runs test ping, and views delivery logs', async ({ page }) => {
    const webhookConfigs = [
      {
        id: 1,
        endpointUrl: 'https://api.merchantstore.com/webhooks/swiftdrop',
        secretKey: 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
        subscribedEvents: 'DELIVERY_STATUS_CHANGED,DELIVERY_DELIVERED',
        active: true,
        createdAt: '2026-11-10T00:00:00Z',
        updatedAt: '2026-11-10T00:00:00Z',
      },
    ];

    await page.route(/\/api\/merchants\/me\/webhooks/, async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/1/test') && method === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 50,
            webhookConfigId: 1,
            eventType: 'webhook.test',
            payload: '{"event":"webhook.test"}',
            httpStatusCode: 200,
            attemptCount: 1,
            status: 'SUCCESS',
            errorMessage: null,
            createdAt: '2026-11-18T10:00:00Z',
            lastAttemptAt: '2026-11-18T10:00:00Z',
          }),
        });
      } else if (url.includes('/1/logs') && method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              id: 99,
              webhookConfigId: 1,
              eventType: 'DELIVERY_STATUS_CHANGED',
              payload: '{"status":"IN_TRANSIT"}',
              httpStatusCode: 200,
              attemptCount: 1,
              status: 'SUCCESS',
              errorMessage: null,
              createdAt: '2026-11-18T12:00:00Z',
              lastAttemptAt: '2026-11-18T12:00:00Z',
            },
          ]),
        });
      } else if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(webhookConfigs),
        });
      } else if (method === 'POST') {
        const postData = JSON.parse(route.request().postData() || '{}');
        const created = {
          id: 2,
          endpointUrl: postData.endpointUrl,
          secretKey: 'mock-auto-generated-secret-key-1234567890',
          subscribedEvents: postData.subscribedEvents,
          active: true,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };
        webhookConfigs.push(created);
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(created),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/merchant/webhooks');
    await expect(page.locator('#webhooks-title')).toHaveText('Merchant Webhooks');
    await expect(page.locator('text=https://api.merchantstore.com/webhooks/swiftdrop')).toBeVisible();

    // Trigger test ping
    await page.click('button:has-text("Test Ping")');
    await expect(page.locator('text=Test Ping Result: SUCCESS')).toBeVisible();

    // View delivery logs
    await page.click('button:has-text("Logs")');
    await expect(page.locator('text=Recent Delivery Logs')).toBeVisible();
    await expect(page.getByText('DELIVERY_STATUS_CHANGED', { exact: true })).toBeVisible();
  });
});
