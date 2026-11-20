# SwiftDrop Courier Module — Database Schema & Compatibility Specification

## 1. Architectural Purpose & Isolation Strategy
The Courier Module is developed as an independent vertical slice during initial development. To guarantee that this module does **not** create conflicting shared tables or block other teams, all development tables are isolated under the `courier_dev_*` namespace:

* `courier_dev_couriers` — Prototype courier profiles.
* `courier_dev_parcels` — Prototype assigned parcel records.
* `courier_dev_parcel_status_history` — Immutable status transition audit records.
* `courier_dev_delivery_proofs` — Delivery confirmation evidence.
* `courier_dev_delivery_failures` — Failure reason audit logs.

These tables are managed strictly through **Flyway migrations** (`src/main/resources/db/migration/`).

---

## 2. Table Specifications & Lifecycle Constraints

### A. Strict Parcel Lifecycle
The parcel state machine strictly adheres to the approved sequence:
```
CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
                                               ↘
                                                FAILED
```
* **Enforced at Database Level:** 
  `CHECK (status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'))`
* Neither `ASSIGNED` nor `PENDING` are valid parcel lifecycle statuses.

### B. Standardized Failure Reasons
When a delivery fails, one of the following validated reasons is required:
* `RECIPIENT_UNAVAILABLE`
* `INCORRECT_ADDRESS`
* `CUSTOMER_REJECTED`
* `DAMAGED_PARCEL`
* `ACCESS_RESTRICTED`
* `WEATHER_DELAY`
* `OTHER`

---

## 3. Seeded Development Identities & Test Scenarios

### Fictional Couriers:
1. **Courier 1 (Primary / Sachin):**
   * **UUID:** `11111111-1111-1111-1111-111111111111`
   * **Name:** Sachin Maleesha
   * **Assigned Zone:** Kurunegala North
   * **Workload:** 12 assigned parcels (6 Delivered, 5 In Progress, 1 Failed) = 50.0% completion rate.
2. **Courier 2 (Secondary / Kamal):**
   * **UUID:** `22222222-2222-2222-2222-222222222222`
   * **Name:** Kamal Perera
   * **Assigned Zone:** Colombo Central
   * **Workload:** 2 separate parcels (`SD-2099-0001`, `SD-2099-0002`).

### Horizontal Privilege Enforcement Test Scenario:
* Courier 1 authenticated via `X-Dev-Courier-Id: 11111111-1111-1111-1111-111111111111` can query and update `SD-1048-7291`.
* Courier 1 attempting to access Courier 2's parcel `SD-2099-0001` is rejected with `403 Forbidden`.

---

## 4. Reconciliation Strategy with Future Shared Modules

| Development Concept | Future Shared Module | Future Reconciliation Action |
| :--- | :--- | :--- |
| `courier_dev_couriers` | **Module 1 (Identity & Gateway)** | `courier_dev_couriers.id` will reference the master `users.id` foreign key. The development registry will be replaced by user records with `ROLE_COURIER`. |
| `courier_dev_parcels` | **Module 2 (Parcels & Pricing)** | The courier parcel entity will map to the shared `parcels` table (or consume via a bounded-context Read Model). Fields like `dimensions`, `weight`, and `price` remain owned by Module 2. |
| `courier_dev_parcel_status_history` | **Module 5 (Events & Notifications)** | When status transitions occur, domain events are published via RabbitMQ to notify customers and merchants, while retaining internal status history for courier audits. |

---

## 5. PostgreSQL & AWS RDS Configuration

Database properties are injected through environment variables without committing credentials to source control:

```bash
# Example AWS RDS Connection Environment Variables
export POSTGRES_URL="jdbc:postgresql://<aws-rds-endpoint>:5432/swiftdrop"
export POSTGRES_USER="<db_master_user>"
export POSTGRES_PASSWORD="<db_master_password>"
```

In `application-dev.properties`:
* `spring.datasource.url=${POSTGRES_URL:jdbc:postgresql://localhost:5432/swiftdrop}`
* `spring.datasource.username=${POSTGRES_USER:postgres}`
* `spring.datasource.password=${POSTGRES_PASSWORD:postgres}`
* `spring.flyway.enabled=true`
* `spring.jpa.hibernate.ddl-auto=validate`
