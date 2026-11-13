import { test, expect, type Page, type Route } from '@playwright/test';

/**
 * Opt-in interactive preview spec for local development and demonstration.
 * Pauses execution with `page.pause()` in headed mode so the developer can
 * interact directly with the Merchant Dashboard UI.
 *
 * Command to run this preview:
 *   cd frontend; npx playwright test e2e/merchant-preview.spec.ts --headed --project=chromium
 */

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

const PREVIEW_ORDERS = [
  {
    id: 201,
    trackingNumber: 'SD-CEY-9821',
    deliveryType: 'EXPRESS',
    status: 'PENDING',
    receiverName: 'Rohan Jayasinghe',
    destinationCity: 'Kandy',
    estimatedDeliveryDate: '2026-11-20',
    createdAt: '2026-11-19T08:30:00Z',
  },
  {
    id: 202,
    trackingNumber: 'SD-CEY-9822',
    deliveryType: 'SAME_DAY',
    status: 'CONFIRMED',
    receiverName: 'Dilani Samarawickrama',
    destinationCity: 'Colombo',
    estimatedDeliveryDate: '2026-11-19',
    createdAt: '2026-11-19T07:15:00Z',
  },
  {
    id: 203,
    trackingNumber: 'SD-CEY-9823',
    deliveryType: 'STANDARD',
    status: 'IN_TRANSIT',
    receiverName: 'Mahesh Gunasekara',
    destinationCity: 'Galle',
    estimatedDeliveryDate: '2026-11-22',
    createdAt: '2026-11-18T14:45:00Z',
  },
  {
    id: 204,
    trackingNumber: 'SD-CEY-9824',
    deliveryType: 'EXPRESS',
    status: 'OUT_FOR_DELIVERY',
    receiverName: 'Nadeeka Bandara',
    destinationCity: 'Kurunegala',
    estimatedDeliveryDate: '2026-11-20',
    createdAt: '2026-11-18T11:20:00Z',
  },
  {
    id: 205,
    trackingNumber: 'SD-CEY-9825',
    deliveryType: 'STANDARD',
    status: 'DELIVERED',
    receiverName: 'Suresh Fernando',
    destinationCity: 'Negombo',
    estimatedDeliveryDate: '2026-11-18',
    createdAt: '2026-11-16T09:00:00Z',
  },
  {
    id: 206,
    trackingNumber: 'SD-CEY-9826',
    deliveryType: 'STANDARD',
    status: 'CANCELLED',
    receiverName: 'Priyantha Wickramasinghe',
    destinationCity: 'Matara',
    estimatedDeliveryDate: '2026-11-17',
    createdAt: '2026-11-15T16:00:00Z',
  },
];

async function setupPreviewMocks(page: Page) {
  const claims = {
    sub: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    name: 'Nimal Perera',
    email: 'nimal@ceylonpureteas.lk',
    roles: ['MERCHANT'],
    entitlements: ['merchant:api-access', 'parcel:create', 'parcel:read-own'],
  };

  const token = createJwt(claims);

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

  await page.route('**/api/merchants/me', async (route: Route) => {
    if (route.request().url().includes('/deliveries') || route.request().url().includes('/dashboard')) {
      return route.fallback();
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        complete: true,
        ownerName: 'Nimal Perera',
        businessName: 'Ceylon Pure Teas (Pvt) Ltd',
        businessEmail: 'orders@ceylonpureteas.lk',
        contactNumber: '+94 11 234 5678',
        addressLine1: '75 Galle Road',
        addressLine2: 'Suite 400, World Trade Center',
        city: 'Colombo',
        province: 'Western',
        postalCode: '00100',
        country: 'Sri Lanka',
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-15T00:00:00Z',
      }),
    });
  });

  await page.route('**/api/merchants/me/dashboard', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        businessName: 'Ceylon Pure Teas (Pvt) Ltd',
        profileComplete: true,
        totalOrders: 12,
        pendingOrders: 3,
        inTransitOrders: 4,
        deliveredOrders: 5,
        countsByStatus: {
          PENDING: 3,
          CONFIRMED: 1,
          PICKED_UP: 1,
          IN_TRANSIT: 1,
          OUT_FOR_DELIVERY: 1,
          DELIVERED: 5,
          FAILED: 0,
          CANCELLED: 1,
        },
        recentOrders: PREVIEW_ORDERS.slice(0, 5),
      }),
    });
  });

  await page.route(/\/api\/merchants\/me\/deliveries(\?.*)?$/, async (route: Route) => {
    const url = new URL(route.request().url());
    const status = url.searchParams.get('status');
    const filtered = status ? PREVIEW_ORDERS.filter((o) => o.status === status) : PREVIEW_ORDERS;

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: filtered,
        page: 0,
        size: 10,
        totalElements: filtered.length,
        totalPages: 1,
      }),
    });
  });

  await page.route(/\/api\/merchants\/me\/deliveries\/\d+/, async (route: Route) => {
    const url = route.request().url();
    const id = parseInt(url.split('/').pop() || '201', 10);
    const order = PREVIEW_ORDERS.find((o) => o.id === id) ?? PREVIEW_ORDERS[0];

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: order.id,
        trackingNumber: order.trackingNumber,
        publicTrackingToken: 'token-preview-' + order.id,
        deliveryType: order.deliveryType,
        status: order.status,
        specialInstructions: 'Handle tea packages with care. Do not expose to moisture.',
        estimatedDeliveryDate: order.estimatedDeliveryDate,
        createdAt: order.createdAt,
        deliveredAt: order.status === 'DELIVERED' ? '2026-11-18T15:30:00Z' : null,
        pickup: {
          contactName: 'Nimal Perera',
          contactPhone: '+94 11 234 5678',
          street: '75 Galle Road, Suite 400',
          city: 'Colombo',
          district: 'Colombo',
          postalCode: '00100',
        },
        destination: {
          contactName: order.receiverName,
          contactPhone: '+94 77 123 4567',
          street: '15 Temple Lane',
          city: order.destinationCity,
          district: order.destinationCity,
          postalCode: '20000',
        },
        parcels: [
          {
            id: 1,
            description: 'Premium Silver Tips Ceylon White Tea (250g)',
            weightKg: 0.35,
            lengthCm: 14.0,
            widthCm: 8.0,
            heightCm: 6.0,
            declaredValue: 4500.0,
          },
          {
            id: 2,
            description: 'Single Estate Broken Orange Pekoe (500g)',
            weightKg: 0.6,
            lengthCm: 18.0,
            widthCm: 12.0,
            heightCm: 8.0,
            declaredValue: 2800.0,
          },
        ],
        trackingEvents: [
          {
            id: 1,
            status: 'PENDING',
            description: 'Delivery order created by Ceylon Pure Teas (Pvt) Ltd',
            locationName: 'Colombo Facility',
            eventTime: order.createdAt,
            recordedByName: 'Nimal Perera',
          },
          ...(order.status !== 'PENDING'
            ? [
                {
                  id: 2,
                  status: 'CONFIRMED',
                  description: 'Booking confirmed and scheduled for dispatch',
                  locationName: 'Colombo Central Hub',
                  eventTime: '2026-11-19T09:00:00Z',
                  recordedByName: 'System Dispatcher',
                },
              ]
            : []),
          ...(order.status === 'IN_TRANSIT' || order.status === 'OUT_FOR_DELIVERY' || order.status === 'DELIVERED'
            ? [
                {
                  id: 3,
                  status: 'IN_TRANSIT',
                  description: 'Package in transit via highway express courier',
                  locationName: 'Southern Expressway',
                  eventTime: '2026-11-19T11:30:00Z',
                  recordedByName: 'Sunil Courier (SD-CR-04)',
                },
              ]
            : []),
          ...(order.status === 'DELIVERED'
            ? [
                {
                  id: 4,
                  status: 'DELIVERED',
                  description: 'Package successfully delivered and signed by recipient',
                  locationName: order.destinationCity,
                  eventTime: '2026-11-19T14:15:00Z',
                  recordedByName: 'Sunil Courier (SD-CR-04)',
                },
              ]
            : []),
        ],
      }),
    });
  });
}

test('interactive preview of merchant dashboard @preview', async ({ page }) => {
  await setupPreviewMocks(page);
  await page.addInitScript(() => {
    sessionStorage.setItem('swiftdrop.refresh', 'mock-preview-refresh');
  });

  await page.goto('/merchant/dashboard');

  await expect(page.locator('text=SwiftDrop Merchant')).toBeVisible();
  await expect(page.locator('text=• Ceylon Pure Teas (Pvt) Ltd')).toBeVisible();

  // Pauses execution for headed interactive inspection
  await page.pause();
});
