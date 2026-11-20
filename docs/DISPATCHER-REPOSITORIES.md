# Dispatcher repositories

The repository stage adds three interfaces under `com.swiftdrop.backend.dispatcher.repository`.
They use the existing delivery model and the courier entities described in
[DISPATCHER-ENTITIES.md](DISPATCHER-ENTITIES.md). No pre-existing Java files were changed.

| Repository | Contribution | Operations |
| --- | --- | --- |
| `DispatcherDeliveryRepository` | 234243J | Cross-customer delivery search, status filtering, tracking-number lookup, dashboard status counts, delivery write-lock lookup. |
| `CourierRepository` | 234140P | Auth-user/profile lookup, paginated availability filters, assignment-candidate selection, courier write-lock lookup. |
| `CourierAssignmentRepository` | 234140P | Current assignment, paginated courier tasks and delivery assignment history, individual and fleet workload counts, active-assignment write-lock lookup. |

## Query behavior

- Delivery search accepts an optional status and optional text. It matches tracking numbers,
  courier names, pickup/destination contacts, streets, cities, and districts. Search is
  case-insensitive and treats `%`, `_`, and backslashes literally. Null/blank text skips text filtering.
- Delivery search returns newest bookings first, using the ID to break timestamp ties. Address
  matching does not multiply results or page totals when both addresses match.
- Dashboard status counts cover all customers. Missing statuses mean zero. The service will
  combine status counts into pending, ongoing, and completed dashboard cards.
- Courier management filters by operational availability. Assignment-candidate selection also
  checks the local auth snapshot for an active account, ACTIVE status, and the COURIER role.
- Courier task lists contain only active assignments, oldest first. Delivery assignment history
  includes both current and closed records, newest first. Both use IDs to break timestamp ties.
- Fleet workload counts omit couriers with no current assignments; individual workload counts
  return zero. Historical assignments never contribute to the workload.

## Service boundaries

These repositories do not expose HTTP endpoints or enforce authorization. Cross-customer queries
must only be used by authorized dispatcher/admin services. For courier-specific queries, take the
auth UUID from the authenticated principal and enforce ownership in the service.

Read lazy relationships inside a service transaction. Assignment candidate results are only a
snapshot: refresh/check the auth identity through `UserDirectory` before assigning, and recheck
availability and workload after acquiring locks.

The `ForUpdate` methods require a write transaction. Lock the delivery first, then relevant courier
profiles in a consistent ID order, before checking/updating assignments. An absent assignment row
cannot be locked, so an assignment lock alone cannot protect a first assignment. Close and flush
an old assignment before inserting a replacement, then synchronize `Delivery.assignCourier(...)`
in that same transaction. These operations remain for the service stage.

The existing delivery model still requires customer ownership; merchant-only orders remain the
integration boundary identified in the entity notes.

## Verification

Repository integration tests exercise filters, paging, literal searches, profile identity,
candidate eligibility, history, workload counts, and JPA write-lock acquisition. They use the
project's H2 database in PostgreSQL mode. They do not simulate concurrent PostgreSQL sessions.

Run the complete backend suite from `backend`:

```powershell
.\mvnw.cmd test
```

Next stage: request/response DTOs, followed by transactional services and controllers.
