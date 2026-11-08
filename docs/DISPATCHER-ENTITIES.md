# Dispatcher entities: 234243J and 234140P

This is the entity stage only. Sources: the project report's individual contributions
(PDF pages 96–98), dispatcher workflow (page 65), ER diagram (page 55), and Dispatch & Courier
class diagram (page 57). All pre-existing Java files remain unchanged.

| Contribution | Entity support |
| --- | --- |
| 234243J — dashboard statistics, order lists, search and filtering | Reuse `Delivery`, `DeliveryStatus`, `DeliveryAddress`, `Parcel`, and `Customer`. Counts and filtered lists will be queries, not new stored dashboard entities. |
| 234140P — courier details and availability | New `dispatcher.entity.Courier` and `CourierAvailability`, linked to the existing `AppUser`. |
| 234140P — assignment and reassignment | New `dispatcher.entity.CourierAssignment`, linked to `Delivery`, `Courier`, and the assigning `AppUser`. |
| 234140P — delivery progress and real-time updates | Reuse `Delivery` and `TrackingEvent`. WebSockets belong in a later service/API stage. |

The report's parcel aggregate corresponds to the existing `Delivery`; the existing `Parcel`
represents an individual package within a delivery. Assignment therefore references `Delivery`.
Local entity IDs follow the existing generated `Long` convention; auth account IDs remain UUIDs.

## New records

- `Courier`: one profile per auth user, phone, optional vehicle type/number, availability,
  timestamps, and an optimistic-lock version. The profile defaults to `OFFLINE` until explicitly
  made available. Availability values are `AVAILABLE`, `BUSY`, and `OFFLINE`.
- `CourierAssignment`: delivery, courier, assigning user, assignment time, active state,
  ending user/time/reason, and an optimistic-lock version. Reassignment preserves earlier rows.

Phone/vehicle fields, the availability values/default, and assignment-ending metadata are
implementation choices supporting the contribution requirements; the diagrams do not fix them.
No credentials, roles, duplicate user accounts, tracking histories, or dashboard counters are added.

## Database guarantees and service integration

Foreign keys reference the existing entities without cascading writes or deletes to them.
Each delivery can have only one active assignment, while each courier can have multiple deliveries.
The unique `(delivery_id, active)` constraint uses `TRUE` for the active row and SQL `NULL` for
closed rows. PostgreSQL and the test H2 database permit multiple historical rows with a null
active value. A check constraint rejects `FALSE` and inconsistent ending metadata.
Use `isActive()` in Java and `active = true` or `active is null` in queries.

The later assignment service must perform the following in one transaction:

1. Resolve users through `UserDirectory`; check dispatcher permissions, the courier's active
   account and COURIER role, delivery eligibility, availability, and workload.
2. Lock the delivery and courier records while deciding assignments. `@Version` prevents lost
   edits to new entities, but does not by itself lock an unchanged courier's availability check.
3. For reassignment, call `endAssignment(...)` on the old record and **flush before inserting**
   its replacement, so the active-assignment unique constraint is released.
4. Persist the new assignment and call the existing
   `delivery.assignCourier(courier.getUser())`. Clear that field when unassigning without a replacement.
5. Publish assignment notifications and status updates after a successful commit through the
   appropriate shared infrastructure.

Constructing an assignment does not change `Delivery`, validate authorization, or send notifications.
Existing deliveries with a courier but no assignment-history row are not backfilled at this stage.
The future service must account for those records before enabling dispatch writes.

## Remaining integration boundary

The current `Delivery` requires a `Customer` and has no merchant-owner relationship. The report's
merchant-only order model cannot be represented fully by that mapping. Merchant order creation
and dispatcher access to merchant orders therefore remain a later integration task; this change
does not invent a second order table or alter the existing ownership model.

Repositories, DTOs, services, authorization, REST endpoints, WebSockets, messaging, and frontend
screens are the next stages. This entity layer does not claim to implement those workflows.

## Verification

`DispatcherEntitiesTest` exercises persistence, reassignment history, active-assignment uniqueness,
multiple deliveries per courier, unique courier profiles, optimistic locking, invalid assignment
end times, and safe relationship deletion. Run from `backend` with `./mvnw.cmd test`.
