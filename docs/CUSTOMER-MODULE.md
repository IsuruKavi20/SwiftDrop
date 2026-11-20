# SwiftDrop Customer Module — implementation and integration

Source: `SwiftDrop-main-3.zip` supplied 13 Nov 2026; all existing team backend features, authentication screens, and frontend theme were retained.

## Customer journey

1. Authenticate at the **existing external SwiftDrop auth service** (`VITE_AUTH_URL`) using email/Google. The app consumes its JWT with `parcel:create` and `parcel:read-own` entitlements; this ZIP does **not** contain the auth server itself.
2. Complete the phone-number profile once if needed.
3. Create delivery -> enter pickup, recipient, and one parcel (weight and dimensions), choose STANDARD/EXPRESS/SAME_DAY.
4. Get estimated fee (server validates and calculates quote, without creating an order).
5. Review -> Confirm. The UI first creates PENDING booking, then calls `/confirm`, resulting in CONFIRMED and unique `SD-...` tracking number. If confirmation fails, the UI retries confirmation of the same pending booking, not a second booking.
6. Dashboard/My Deliveries/history, search and filter.
7. Track own delivery and see timeline (15-second automatic refresh); public tracking by tracking number hides phone, street, names and contents.
8. After a courier/dispatcher sets DELIVERED, download a basic PDF receipt (owner only). Saved addresses and phone profile management are also available.

## Endpoints

| Method | Path | Access |
|---|---|---|
| POST | `/api/customers/me` | Customer entitlement, profile completion |
| GET/PUT | `/api/customers/me` | Customer entitlement |
| GET/POST/PUT/DELETE | `/api/customers/me/addresses` (+ `/{addressId}`) | Customer |
| GET | `/api/customers/me/dashboard` | Own customer |
| POST | `/api/customers/me/deliveries/estimate` | `parcel:create` — **new** |
| POST | `/api/customers/me/deliveries` | `parcel:create` |
| POST | `/api/customers/me/deliveries/{id}/confirm` | `parcel:create` |
| GET | `/api/customers/me/deliveries` | `parcel:read-own`, paginated |
| GET | `/api/customers/me/deliveries/{id}` | Own customer |
| GET | `/api/customers/me/deliveries/{id}/receipt` | Own customer, only DELIVERED — **new** |
| GET | `/api/customers/me/tracking/{trackingNumber}` | Own customer |
| GET | `/api/public/tracking/{trackingNumber}` | Public, non-private response |
| GET | `/api/public/tracking/link/{token}` | Public, unguessable token |

## Database

Already included: customer tables and Flyway V1–V8. New migration **V9** adds nullable `delivery.estimated_fee NUMERIC(12,2)` and non-negative check. Past bookings do not get invented amounts. New bookings persist the quote returned by the same `CustomerPricingService` used by `/estimate`. Migration must run on target DB before startup (`ddl-auto=validate`). **Do not delete or rename your teammates' migrations.**

## Pricing integration boundary

The quote is **illustrative demo pricing, not official courier tariffs**: within same district base LKR 350, other district LKR 650, each kg above 1 kg costs LKR 120, EXPRESS + LKR 350, SAME_DAY + LKR 650. This supports the assignment's "show estimated fee before confirming" flow; the dispatcher/admin team's zone/pricing tables and authoritative tariff policy are not present in the supplied snapshot. Integrate them before claiming real commercial pricing.

## Tracking integration boundary

The backend already records `tracking_event` records, including courier/dispatcher updates when those modules implement the flow. Customer pages refresh every 15 seconds; this is **polling**, not WebSockets/SSE. The assignment's overview accepts polling for real-time status, while its implementation section names WebSockets/SSE. Add an SSE/WebSocket push adapter with the integration team if strict demonstration of that requirement is necessary. **This ZIP does not implement live map/GPS or courier status mutation.**

## Auth and notifications integration boundary

Login/signup/Google UI already belongs to the team, backed by `https://auth.getswiftdrop.me` by default. There is no bypass or mock customer token. The missing Jackson2 compatibility module in Spring Boot 4 was added so the existing NotificationEventListener can inject ObjectMapper. RabbitMQ broker and notification services still need their own configuration and tests by their owners.

## Running (macOS Intel)

Use a **Git clone** for development, not the ZIP alone. Copy changed files from this ZIP onto your `feature/customer-module` branch after verifying `git status` is clean; do not overwrite newer teammates' changes blindly.

```bash
# Terminal 1: PostgreSQL must be running and SwiftDrop_db must exist.
cd ~/Desktop/SwiftDrop/backend
./mvnw spring-boot:run
# Terminal 2
cd ~/Desktop/SwiftDrop/frontend
npm ci
npm run dev
```

Set `DB_USERNAME`, `DB_PASSWORD` and other environment variables locally if needed. Never commit passwords or `.env` secrets. Open `http://localhost:5173` then sign in via the team's auth service.

Build and test:

```bash
cd backend && ./mvnw test
cd ../frontend && npm run build && npm run lint
```

## Test status in this packaged work

- Source-level TypeScript/TSX syntax checked using TypeScript's transpiler.
- Spring Boot test execution and full frontend build could **not** run in the authoring environment because npm/Maven dependencies could not be downloaded. They **must** be run on a developer Mac with network access before PR/merge.
- Added two pricing tests and authenticated API tests for quote and receipt authorization. Existing tests and teammates' code were kept.

## UI routes

- `/customer/dashboard`
- `/customer/deliveries/new`
- `/customer/deliveries`
- `/customer/deliveries/:id`
- `/customer/track`
- `/customer/profile`
- `/track` and `/track/{trackingNumber}` (public)

## Known remaining limitations / checks

- Shared login service, notification infrastructure, courier status updates and dispatcher assignment are team-managed external dependencies.
- Fee rules must eventually follow admin-configured tariffs; no payment gateway is implemented/required.
- Only one parcel can be entered through the current UI, though the existing backend accepts up to 20 per delivery.
- PDF receipt uses standard Latin PDF font; unsupported non-Latin recipient names are replaced with `?`. For Sinhala/Tamil, use an embedded Unicode font in the deployment runtime.
- Browser receipt download depends on authenticated JWT and delivered status.
- End-to-end manual tests are still required with a live auth service, database and courier event updates.
