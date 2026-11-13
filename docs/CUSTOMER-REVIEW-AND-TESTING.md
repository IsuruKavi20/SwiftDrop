# SwiftDrop — Customer Module review and testing

Scope: customer-facing features in `SwiftDrop-main-3.zip` plus the customer implementation previously applied to `feature/customer-module`. **This is an implementation review, not an end-to-end certification.**

## Customer requirements and implementation

| Requirement | Code/route | Status |
|---|---|---|
| Customer signup/login incl. Google | Shared auth UI and external SwiftDrop identity service | Existing team integration; requires live auth test |
| Customer profile / saved addresses | `/customer/profile`, `/api/customers/me`, addresses APIs | Implemented; add/edit/remove with authorization |
| Create delivery request | `/customer/deliveries/new`; POST `/api/customers/me/deliveries` | Implemented; required fields, phone validation, dimensions, recipient/pickup |
| Show delivery fee before booking | POST `/api/customers/me/deliveries/estimate` | Implemented; provisional example tariff, not admin-controlled |
| Review/confirm booking | POST `/api/customers/me/deliveries/{id}/confirm` | Implemented; retry preserves pending booking to avoid duplicate creation |
| Unique tracking number | Existing `TrackingCodeGenerator` and database constraint | Implemented |
| Private tracking | `/customer/track` and `/customer/deliveries/{id}` | Owner-only API, status timeline, 15-second polling |
| Public tracking | `/track`, `/track/{number}` | No login required; personal names, contact numbers and street addresses omitted |
| Delivery history | `/customer/deliveries`, dashboard | Paginated, search/filter by status |
| Delivery receipt | GET `/api/customers/me/deliveries/{id}/receipt` | PDF, private and delivered-only (project design decision) |
| Mobile-friendly | Responsive customer portal CSS | Implemented; still requires device/browser confirmation |
| Notifications/status events | Team notification/courier modules | Integration-dependent; customer displays persisted events when available |

## Important data-model decision

Existing team database/API states are `PENDING` (unconfirmed booking) and `CONFIRMED` (confirmed booking). The customer UI displays the **confirmed** state as **Created**, matching the assignment's `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED/FAILED` progress. We deliberately **did not rename stored enums** because this would break other team modules and migrations. `PENDING` displays as **Awaiting confirmation**. `CANCELLED` is a recognized shared enum but customer cancellation is outside the provided assignment's customer requirements and isn't implemented as a new action.

## Security and correctness

- Owner-only endpoints obtain user UUID from validated JWT, never a client-submitted owner ID.
- Public tracking uses a restricted response without private details; private tracking uses the owner-scoped API.
- Download receipt checks delivery ownership and eligible status.
- Booking creates a server-generated tracking number and quote; client cannot choose either.
- Phone inputs now use the same format as backend (`+` optional; 9–15 digits).
- UI shows all parcels in a delivery detail, not only the first.
- Booking confirmation retry avoids a second booking after a partial failure.
- Saved-address editing and autofill use the existing customer address APIs.
- Pricing constants remain demonstrative; an Admin Pricing feature/zone validation must be integrated by the responsible member before production.

## Required verification on developer Mac BEFORE pull request merge

1. `cd frontend && npm ci && npm run build && npm run lint`
2. `cd ../backend && ./mvnw test`
3. Start PostgreSQL and the configured auth service; start backend and React frontend.
4. Sign up with email and verify email; log in; try Google login (depends on external service).
5. Complete customer profile; add, edit, remove a saved address; use saved address in pickup form.
6. Estimate fee for same-district / cross-district and service variants; check validation for invalid phone, zero weight, missing recipient.
7. Confirm a new booking and verify **one** record and a tracking number in My Deliveries.
8. Search and filter history, reload detail page, confirm estimated fee persists.
9. Verify private tracking refuses another customer's tracking number; public tracking hides full names, streets, numbers, parcel contents.
10. Simulate status updates through the courier/admin integration; verify timeline advances after polling.
11. Test PDF receipt for a delivered booking and denial for undelivered or another customer's booking.
12. Open dashboard, form, history, tracking, receipt on desktop and mobile widths; test with keyboard.
13. Confirm no unrelated auth/merchant/courier changes are in the branch diff, and `git status` is clean after commits.

## Verification performed in this packaging environment

- Frontend TypeScript/TSX parsed/transpiled with global TypeScript compiler; no syntax parse errors.
- Customer status labels, routes, API contract references, migration numbering and response privacy inspected.
- Maven integration tests and full Vite build **not executed here**: Maven/npm dependencies unavailable in this container. A failed local dependency fetch is not evidence the source is broken; run the commands above on your Mac.
- No live auth/RabbitMQ/dispatcher/courier end-to-end testing performed in this environment.

## Known team dependencies and limits

- The identity/auth service must issue valid CUSTOMER entitlements and remain reachable for login.
- Real courier status updates and RabbitMQ event publishing are dependent on other modules. Polling works against persisted status changes, but this customer package does not supply WebSocket/SSE.
- There is no integrated admin-managed tariff or restricted-zone validation in this source snapshot. The current fee is explicitly an estimate for demonstration.
- Receipts are generated on demand only after `DELIVERED`; they are not payment receipts or proof that a charge was collected.
- Standard PDF fonts have limited Sinhala/Tamil glyph coverage; a Unicode font solution is required for multilingual receipt names.
- Do not claim all features are production-ready until the manual and automated checks pass.
