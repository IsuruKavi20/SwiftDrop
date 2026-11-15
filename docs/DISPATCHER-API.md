# Dispatcher API (deliveries and courier assignment)

The admin's dispatch screens. In SwiftDrop admins do the dispatch work, so every call needs the **ADMIN** role
(`hasRole('ADMIN')`; the auth service's DISPATCHER role is not used). Errors use the shared problem JSON
(`docs/ERRORS.md`). Code: `dispatcher/controller/DispatcherController.java`.

| Call | What |
|---|---|
| `GET /api/dispatcher/deliveries?status=&search=&page=0&size=10` | Every delivery (customers and merchants), newest first. `search` matches tracking number, courier name, contact names, streets, cities, districts. `size` max 50. |
| `GET /api/dispatcher/deliveries/unassigned?search=&page=0&size=50` | Confirmed deliveries with no courier, oldest first. |
| `GET /api/dispatcher/deliveries/counts` | `{ "statuses": { "PENDING": 0, … }, "unassigned": 2 }`, every status present. |
| `GET /api/dispatcher/deliveries/{id}` | One delivery plus `assignments` (its courier history, newest first). |
| `POST /api/dispatcher/deliveries/{id}/assignment` `{ "courierId": 3, "reason": "…" }` | Assign or reassign. Answers like the details call. |
| `GET /api/dispatcher/couriers?availability=` | Courier profiles with `activeDeliveries` and `currentTrackingNumbers`. |
| `GET /api/dispatcher/couriers/assignable` | Available couriers whose account is active with the Courier role, lightest workload first. |

`courierId` is the courier **profile** id (`courier.courier_id`), not the person's auth id.

## Assignment rules
- Only CONFIRMED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY or FAILED deliveries → otherwise `409`.
- The courier must be AVAILABLE, active in the auth service and still have the COURIER role → `409` (blocked or
  deleted account: `404`).
- Not the courier already assigned → `409`.
- Reassigning (the delivery already has a courier) needs `reason` (max 500) → `400`. It is stored as the ended
  assignment's `end_reason`, with who ended it and when.
- **The delivery's status is not changed.** The courier records the next step. A FAILED delivery stays FAILED, with
  its new courier, until that courier's next attempt.
- Locking: the delivery first, then the couriers involved in id order, so two admins assigning at the same moment
  never both succeed.

Tests: `DispatcherApiTest`.
