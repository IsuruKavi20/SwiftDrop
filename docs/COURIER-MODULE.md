# SwiftDrop Courier Module — Architecture, Implementation and Handover

## Overview

The **SwiftDrop Courier Module** provides the complete operational backend and modern responsive portal for delivery personnel. It handles assigned route parcel fulfillment, real-time delivery state transitions, digital proof of delivery capture, structured delivery failure logging, and courier operational profile management.

---

## 1. Module Features

- **Operational Dashboard:**
  - Real-time KPI statistics: Total Assigned, In Progress, Delivered, Failed/Delayed, and Shift Fulfillment Percentage.
  - Active route parcels list with quick status-filtering (All, Pending, In Transit, Delivered).
  - Chronological activity timeline of assigned deliveries.
  - Responsive layout optimized for mobile smartphones and desktop tablets.

- **My Deliveries (Route Management):**
  - Live query of deliveries assigned specifically to the authenticated courier (`findByCourierId`).
  - Search by tracking number, recipient name, city, or delivery address.
  - Status segment filtering (`PENDING`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`).
  - Mobile card view and desktop tabular view.

- **Delivery Details & Execution:**
  - Complete recipient contact details and click-to-call action.
  - Address copy and one-click Google Maps GPS navigation.
  - Visual 5-step lifecycle progress tracker: `CONFIRMED` $\to$ `PICKED_UP` $\to$ `IN_TRANSIT` $\to$ `OUT_FOR_DELIVERY` $\to$ `DELIVERED`.
  - Permitted transition enforcement rejecting out-of-order jumps (e.g. cannot transition directly from `CONFIRMED` to `DELIVERED`).
  - Digital Proof of Delivery capture: Recipient signee name, verification type, and courier handover remarks.
  - Incident/Failure reporting: Structured failure reasons (`RECIPIENT_UNAVAILABLE`, `INCORRECT_ADDRESS`, `CUSTOMER_REJECTED`, `ACCESS_RESTRICTED`, `DAMAGED_PARCEL`, `FORCE_MAJEURE`) with required audit remarks.

- **Delivery History & Audit Archive:**
  - Filterable closed delivery archive (`DELIVERED` and `FAILED`).
  - Permanent audit trail of recorded status events, location stamps, and sign-off records.

- **Courier Profile & Shift Settings:**
  - Authenticated courier identity and assigned service territory/zone.
  - Fleet vehicle designation (`MOTORCYCLE`, `VAN`, `THREE_WHEELER`, etc.).
  - Self-service contact phone number update with server-side validation.
  - Explicit operational territory policy safeguards preventing unauthorized reassignments.

- **Notifications & Broadcasts:**
  - Dedicated dispatch broadcast viewing area.
  - Clear separation between real API empty state and simulated development alerts.

---

## 2. Architecture & Design Patterns

### Spring Modulith Compliance
The Courier Module is encapsulated under package `com.swiftdrop.backend.courier`:
- **Named Interface:** Exposes the `events` interface via `com.swiftdrop.backend.courier.event.MerchantCourierStatusChanged` to publish committed delivery lifecycle transitions for merchant webhooks and downstream notifications without circular dependencies.
- **Repository Isolation:** `CourierDeliveryRepository` accesses canonical `Delivery` entities using horizontal privilege boundaries (`findByTrackingNumberAndCourierId`).
- **Pessimistic Concurrency:** Employs `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`lockByTrackingNumber`) during critical status mutations to prevent concurrent double-updates.

### Frontend Architecture
- **Dual Mode Data Architecture:**
  - `HttpCourierDataServiceImpl`: Production-grade HTTP client communicating with backend REST endpoints via `authorizedFetch`. Automatically transmits JWT bearer tokens and standardizes RFC 9457 `ProblemDetail` errors.
  - `MockCourierDataServiceImpl`: Isolated local development and demonstration implementation.
  - Forced production safety: `env.isCourierMockMode` evaluates `import.meta.env.DEV && import.meta.env.VITE_COURIER_MOCK_MODE === 'true'`, guaranteeing that mock mode is compiled out of production bundles.

---

## 3. Secured API Endpoints

All endpoints require HTTP Bearer JWT authentication from the SwiftDrop Identity Provider.

| Method | Path | Required Role / Authority | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/couriers/me/dashboard` | `ROLE_COURIER` / `delivery:read-own` | Retrieves real-time shift stats and fulfillment metrics. |
| `GET` | `/api/couriers/me/deliveries` | `ROLE_COURIER` / `delivery:read-own` | Returns paginated list of assigned deliveries with optional status and search filters. |
| `GET` | `/api/couriers/me/deliveries/{trackingNumber}` | `ROLE_COURIER` / `delivery:read-own` | Retrieves full delivery details, tracking timeline, and proof records (horizontal isolation enforced). |
| `PATCH` | `/api/couriers/me/deliveries/{trackingNumber}/status` | `ROLE_COURIER` / `delivery:update-status` | Advances lifecycle (`PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`). |
| `POST` | `/api/couriers/me/deliveries/{trackingNumber}/confirm` | `ROLE_COURIER` / `delivery:update-status` | Confirms delivery with proof of delivery (recipient signature name, notes, timestamp). |
| `POST` | `/api/couriers/me/deliveries/{trackingNumber}/fail` | `ROLE_COURIER` / `delivery:update-status` | Marks delivery failed with structured reason and mandatory explanation. |
| `GET` | `/api/couriers/me/profile` | `ROLE_COURIER` | Retrieves operational profile, vehicle type, and assigned zone. |
| `PUT` | `/api/couriers/me/profile` | `ROLE_COURIER` | Updates courier contact phone number. |

---

## 4. Cross-Module Integration

### Dispatcher $\to$ Courier Integration
1. Dispatcher assigns a confirmed delivery via `POST /api/dispatcher/deliveries/{id}/assignment` with `{ "courierId": <courierId> }`.
2. The assignment updates `delivery.courier_id`.
3. The delivery immediately appears in the assigned courier's `/api/couriers/me/deliveries` and dashboard.
4. Other couriers receive `403 Forbidden` if attempting to view or modify that delivery.
5. If the Dispatcher reassigns the delivery to Courier B, Courier A immediately loses access (`403 Forbidden`) and Courier B gains full delivery control.

### Courier $\to$ Merchant & Customer Integration
When the courier updates delivery status:
1. `Delivery.status` is updated and a new `TrackingEvent` is appended to the shared delivery timeline.
2. `MerchantCourierStatusChanged` is published via Spring's `ApplicationEventPublisher`.
3. Downstream `MerchantWebhookListener` catches the event and queues webhook notifications to merchant systems.
4. Customers viewing public tracking or their customer portal immediately see the updated status.

---

## 5. Database Schema & Flyway Migrations

The Courier Module utilizes database migration **V21**:
- `V21__create_courier_profile_tables.sql`:
  - `courier_profile`: Maps `user_id` (foreign key to `app_user.id`), `phone_number`, `vehicle_type`, `assigned_zone`, `is_active`, `created_at`, `updated_at`.
  - `delivery_proof`: Maps `delivery_id` (FK to `delivery.id`), `courier_id` (FK to `app_user.id`), `recipient_sign_name`, `proof_type`, `delivery_notes`, `proof_media_url`, `captured_at`.
  - `delivery_failure`: Maps `delivery_id` (FK to `delivery.id`), `courier_id` (FK to `app_user.id`), `failure_reason`, `failure_remarks`, `reported_at`.
- All tables enforce foreign keys and non-negative timestamps with strict index coverage.

---

## 6. How to Run & Verify

### Frontend Execution
```bash
cd frontend

# Install dependencies
npm install

# Run in Real API Mode (Default, expects backend on http://localhost:8080)
npm run dev

# Run in Development Mock Mode (Simulated data for UI inspection)
VITE_COURIER_MOCK_MODE=true npm run dev
```

### Backend Execution
```bash
cd backend

# Run with standard in-memory test database (H2 PostgreSQL mode, Flyway V1-V21)
./mvnw clean test

# Run isolated Courier integration and security suite
./mvnw test -Dtest="CourierDispatcherIntegrationTest,CourierControllerTest,CourierServiceTest,CourierModuleRepositoriesTest,ModularityTest"
```

### Frontend Automated Testing & Linting
```bash
cd frontend
npm test
npm run build
npm run lint
```

---

## 7. Known External Verification Dependencies

1. **Live Courier Account Provisioning:**
   - Real hosted authentication at `https://auth.getswiftdrop.me` requires a courier user account provisioned by the Authentication team with role `COURIER` and claims `delivery:read-own`, `delivery:update-status`.
   - In the absence of live hosted credentials, real API integration is fully verified via automated MockMvc tests (`CourierControllerTest`, `CourierDispatcherIntegrationTest`) using real Spring Security JWT decoders.

2. **Dedicated PostgreSQL Environment:**
   - Automated tests run against the repository's configured in-memory PostgreSQL-compatible database (`MODE=PostgreSQL`) with Flyway migrations V1–V21 validated (`ddl-auto=validate`).
   - A dedicated profile `application-postgres-test.properties` is configured for local standalone PostgreSQL instances without any risk of connecting to shared AWS RDS.
