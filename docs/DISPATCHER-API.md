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
| `GET /api/dispatcher/couriers/candidates` | Active people with the Courier role and no profile yet: `[{ "userId", "name" }]`, by name. |
| `POST /api/dispatcher/couriers` `{ "userId", "phoneNumber", "vehicleType", "vehicleNumber", "availability" }` | Create a courier's work profile → `201` with the courier (same shape as the list). |
| `PUT /api/dispatcher/couriers/{courierId}` `{ "phoneNumber", "vehicleType", "vehicleNumber", "availability" }` | Edit phone, vehicle, availability. |

`courierId` is the courier **profile** id (`courier.courier_id`), not the person's auth id. Each courier in the lists
also carries `userId` (the auth id), `accountActive` and `courierRole` (false once auth blocks the account or removes the
role). Emails are not returned: the admin page reads them from the auth service with the admin's own token.

## Courier profile rules
- Create: `userId` required (`400`); unknown, blocked or deleted person → `404`; no Courier role or already has a
  profile → `409`. A person the backend hasn't seen yet is copied from the auth service first.
- Phone: 9 to 20 digits and spaces, optional leading `+`. Vehicle type and number: at most 30 characters, blank = none.
- Only an active account with the Courier role can be made `AVAILABLE` (`409`). Going `BUSY` or `OFFLINE` keeps the
  courier's current deliveries.
- Editing locks the courier row like assignment does, so a delivery can't be assigned while the courier is taken off duty.
- Blocking a courier or removing the role is done in the auth service (Users and Roles page).

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

Tests: `DispatcherApiTest`, `CourierProfileApiTest`.

## Reports (Reports page and Admin Overview)
`GET /api/admin/reports?days=7` (ADMIN only; `days` 1 to 90, default 7, `1` = today) → the last `days` days, today
included:

```json
{ "from": "2026-11-09", "to": "2026-11-15", "bookingsCreated": 23, "delivered": 17, "failedAttempts": 3,
  "perDay":   [{ "day": "2026-11-09", "bookings": 3, "delivered": 2, "failedAttempts": 0 }, …],
  "couriers": [{ "courierId": 1, "name": "Kasun Fernando", "delivered": 9, "failedAttempts": 1, "active": 2 }, …] }
```

- **Days are Sri Lanka days** (Asia/Colombo), not UTC: a booking at 01:30 in Colombo counts for that day.
- Bookings: deliveries created in the period, any status. Delivered: by delivery date (`delivered_at`).
- Failed attempts: FAILED tracking events, so a parcel that failed once and arrived later counts in both. Each is
  credited to the courier who recorded it, or else to the delivery's current courier.
- `perDay` has every day of the period, oldest first. `couriers` lists every courier profile, most deliveries
  first; `active` = active assignments right now.
- The Admin Overview's "Bookings today" is `bookingsCreated` with `days=1`.

Tests: `AdminReportApiTest`.
