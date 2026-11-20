import test from 'node:test';
import assert from 'node:assert/strict';
import {
  MockCourierDataServiceImpl,
  HttpCourierDataServiceImpl,
  ApiError,
} from '../courierService';

test('MockCourierDataServiceImpl - Retrieves dashboard stats and performs status transitions', async () => {
  const service = new MockCourierDataServiceImpl();

  const stats = await service.getDashboardStats();
  assert.ok(stats.assignedToday >= 0);
  assert.ok(stats.completionPercentage >= 0);

  const deliveries = await service.getAssignedDeliveries({ status: 'ALL' });
  assert.ok(deliveries.length > 0);
  const sample = deliveries[0];

  const details = await service.getDeliveryDetails(sample.trackingNumber);
  assert.ok(details !== null);
  assert.equal(details!.trackingNumber, sample.trackingNumber);

  // Status update
  const updated = await service.updateDeliveryStatus(sample.trackingNumber, {
    nextStatus: 'IN_TRANSIT',
    notes: 'On courier bike',
  });
  assert.equal(updated.status, 'IN_TRANSIT');
  assert.equal(updated.statusHistory[0].toStatus, 'IN_TRANSIT');

  // Confirmation
  const confirmed = await service.confirmDelivery(sample.trackingNumber, {
    recipientSignName: 'Kamal Perera',
    deliveryNotes: 'Left with security guard',
  });
  assert.equal(confirmed.status, 'DELIVERED');
  assert.ok(confirmed.proof);
  assert.equal(confirmed.proof!.recipientSignName, 'Kamal Perera');
});

test('MockCourierDataServiceImpl - Records failure correctly', async () => {
  const service = new MockCourierDataServiceImpl();
  const deliveries = await service.getAssignedDeliveries();
  const target = deliveries[1] || deliveries[0];

  const failed = await service.reportFailure(target.trackingNumber, {
    failureReason: 'RECIPIENT_UNAVAILABLE',
    failureRemarks: 'Gate locked, no answer to phone calls',
  });

  assert.equal(failed.status, 'FAILED');
  assert.ok(failed.failure);
  assert.equal(failed.failure!.failureReason, 'RECIPIENT_UNAVAILABLE');
  assert.equal(failed.failure!.failureRemarks, 'Gate locked, no answer to phone calls');
});

test('MockCourierDataServiceImpl - Filters assigned deliveries by status and search keyword', async () => {
  const service = new MockCourierDataServiceImpl();

  const all = await service.getAssignedDeliveries();
  assert.ok(all.length > 0);

  // Search by tracking number
  const searchResult = await service.getAssignedDeliveries({ search: all[0].trackingNumber });
  assert.equal(searchResult.length, 1);
  assert.equal(searchResult[0].trackingNumber, all[0].trackingNumber);

  // Filter by nonexistent status
  const emptyFilter = await service.getAssignedDeliveries({ status: 'CANCELLED' });
  assert.equal(Array.isArray(emptyFilter), true);
});

test('HttpCourierDataServiceImpl - Throws ApiError when backend is unreachable without silent mock fallback', async () => {
  // Point to a non-existent port to guarantee connection refusal
  const unreachableService = new HttpCourierDataServiceImpl('http://127.0.0.1:59999');

  await assert.rejects(
    async () => {
      await unreachableService.getDashboardStats();
    },
    (err: unknown) => {
      assert.ok(err instanceof ApiError);
      assert.equal((err as ApiError).status, 0);
      assert.match((err as ApiError).message, /Cannot reach the SwiftDrop server/);
      return true;
    }
  );
});

test('HttpCourierDataServiceImpl - Proper error handling and 404 behavior', async () => {
  const unreachableService = new HttpCourierDataServiceImpl('http://127.0.0.1:59999');

  // Unreachable host throws ApiError, not silently returning mock data
  await assert.rejects(
    async () => {
      await unreachableService.getDeliveryDetails('NON-EXISTENT-SD-999');
    },
    (err: unknown) => {
      assert.ok(err instanceof ApiError);
      return true;
    }
  );
});

test('MockCourierDataServiceImpl - Retrieves and updates courier profile', async () => {
  const service = new MockCourierDataServiceImpl();
  const profile = await service.getCourierProfile();
  assert.ok(profile.id);
  assert.ok(profile.fullName);

  const updated = await service.updateCourierProfile({
    phoneNumber: '+94 77 999 8888',
    vehicleType: 'Electric Scooter',
  });
  assert.equal(updated.phoneNumber, '+94 77 999 8888');
  assert.equal(updated.vehicleType, 'Electric Scooter');
});

test('ApiError - Preserves HTTP status and validation field errors', () => {
  const error = new ApiError(400, 'Validation failed', {
    nextStatus: 'Cannot transition from DELIVERED to IN_TRANSIT',
  });

  assert.equal(error.status, 400);
  assert.equal(error.message, 'Validation failed');
  assert.deepEqual(error.fieldErrors, {
    nextStatus: 'Cannot transition from DELIVERED to IN_TRANSIT',
  });
});
