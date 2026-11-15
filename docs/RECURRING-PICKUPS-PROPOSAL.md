# Architecture Proposal: Automated Recurring Pickups for SwiftDrop Merchants

## 1. Executive Summary

High-volume merchants (such as ecommerce retailers, tea exporters, and electronics vendors) require consistent, scheduled courier pickups at regular intervals (daily, weekdays, or specific days per week) without having to manually submit individual pickup requests each morning.

This document presents the complete architectural specification for the **Automated Recurring Pickups Engine**. It details the proposed database schema, background scheduling worker, idempotency guarantees, error recovery, REST APIs, and frontend user experience.

---

## 2. Database Schema Specification

The proposed implementation requires two new database tables. Because no Flyway migration should be added until cross-team review is finalized, the DDL is documented below for future migration (`V15__merchant_recurring_pickups.sql`).

```sql
-- Recurring pickup schedules created by merchants
CREATE TABLE recurring_pickup_schedule (
    schedule_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id          BIGINT NOT NULL REFERENCES merchant (merchant_id) ON DELETE CASCADE,
    title                VARCHAR(150) NOT NULL,
    frequency            VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY', 'WEEKDAYS', 'WEEKLY', 'BIWEEKLY', 'CUSTOM')),
    cron_expression      VARCHAR(50),
    days_of_week         VARCHAR(50), -- Comma-separated: MON,TUE,WED,THU,FRI,SAT,SUN
    pickup_time          TIME NOT NULL,
    timezone             VARCHAR(50) NOT NULL DEFAULT 'Asia/Colombo',

    -- Pickup contact & location details
    contact_name         VARCHAR(100) NOT NULL,
    contact_phone        VARCHAR(20) NOT NULL,
    street_address       VARCHAR(255) NOT NULL,
    city                 VARCHAR(100) NOT NULL,
    district             VARCHAR(100) NOT NULL,
    postal_code          VARCHAR(15),

    -- Expected parcel volume & logistics profile
    estimated_parcels    INTEGER NOT NULL DEFAULT 1,
    vehicle_type_needed  VARCHAR(30) NOT NULL DEFAULT 'MOTORCYCLE' CHECK (vehicle_type_needed IN ('MOTORCYCLE', 'THREE_WHEELER', 'VAN', 'LIGHT_TRUCK')),
    special_instructions TEXT,

    -- Status and validity period
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    start_date           DATE NOT NULL,
    end_date             DATE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recurring_schedule_merchant ON recurring_pickup_schedule (merchant_id);
CREATE INDEX idx_recurring_schedule_active ON recurring_pickup_schedule (active, start_date, end_date);

-- Execution log recording each generation attempt
CREATE TABLE recurring_pickup_execution_log (
    execution_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id          BIGINT NOT NULL REFERENCES recurring_pickup_schedule (schedule_id) ON DELETE CASCADE,
    scheduled_date       DATE NOT NULL,
    execution_time       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'GENERATED', 'DISPATCHED', 'SKIPPED', 'FAILED')),
    delivery_id          BIGINT REFERENCES delivery (delivery_id) ON DELETE SET NULL,
    error_message        TEXT,
    retry_count          INTEGER NOT NULL DEFAULT 0,

    -- Idempotency constraint: A schedule can only be executed once per calendar date
    CONSTRAINT uk_schedule_execution_date UNIQUE (schedule_id, scheduled_date)
);

CREATE INDEX idx_recurring_log_schedule ON recurring_pickup_execution_log (schedule_id, scheduled_date);
```

---

## 3. Spring Boot Architecture & Scheduling Worker

### Module Ownership & Modulith Boundary
- **Module:** `merchant` (owns `RecurringPickupSchedule` entity and merchant CRUD APIs)
- **Integration with Dispatcher:** When a scheduled pickup triggers, an internal Spring application event (`RecurringPickupTriggeredEvent`) is published. The `dispatcher` module listens and allocates an active courier in the merchant's geographic sector.

### Scheduling Worker (`@Scheduled`)

To prevent duplicate runs across scaled backend instances, a distributed lock (PostgreSQL advisory lock or ShedLock) is applied:

```java
@Service
public class RecurringPickupWorker {

    private final RecurringPickupRepository scheduleRepository;
    private final RecurringPickupExecutionLogRepository logRepository;
    private final MerchantDeliveryService deliveryService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Executes every hour at minute 0 to identify schedules due for pickup within the next window.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Colombo")
    @SchedulerLock(name = "RecurringPickupWorker_processDuePickups", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    @Transactional
    public void processDuePickups() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Colombo"));
        LocalTime windowEnd = now.plusHours(2);

        List<RecurringPickupSchedule> due = scheduleRepository.findDueSchedules(today, now, windowEnd);
        for (RecurringPickupSchedule schedule : due) {
            processScheduleSafely(schedule, today);
        }
    }

    private void processScheduleSafely(RecurringPickupSchedule schedule, LocalDate targetDate) {
        // Step 1: Idempotency check
        if (logRepository.existsByScheduleIdAndScheduledDate(schedule.getId(), targetDate)) {
            return;
        }

        RecurringPickupExecutionLog log = new RecurringPickupExecutionLog(schedule, targetDate);
        try {
            // Step 2: Book pickup / delivery in the system
            Delivery created = deliveryService.createRecurringPickupDelivery(schedule, targetDate);
            log.markGenerated(created.getId());

            // Step 3: Publish event for dispatcher
            eventPublisher.publishEvent(new RecurringPickupGeneratedEvent(schedule.getId(), created.getId(), targetDate));
        } catch (Exception ex) {
            log.markFailed(ex.getMessage());
        } finally {
            logRepository.save(log);
        }
    }
}
```

---

## 4. Idempotency Guarantees & Concurrency

1. **Database Unique Constraint:** `uk_schedule_execution_date UNIQUE (schedule_id, scheduled_date)` enforces that even if two worker threads or microservice nodes attempt to execute the same schedule for the same date, only one database row can be created. The second will encounter a database constraint violation and gracefully terminate.
2. **Deterministic Pre-Locking:** The log record is inserted with status `PENDING` before external calls are made, preventing race conditions.
3. **Pessimistic / Optimistic Locking:** Schedule rows have JPA `@Version` optimistic locking to prevent concurrent edits during worker processing.

---

## 5. Failure Handling & Merchant Notifications

1. **Closed Store / Holidays:**
   - Merchants can define an array of blackout dates (e.g. public holidays, annual store maintenance).
   - If a scheduled pickup falls on a blackout date, the worker records status `SKIPPED`.
2. **Capacity Exhaustion / Dispatch Delay:**
   - If no couriers are available in the sector, the status becomes `PENDING` with retry backoff up to 3 times before transitioning to `FAILED`.
   - On `FAILED`, an immediate notification is dispatched via SMS/Email to the merchant contact number:
     *"Your scheduled SwiftDrop pickup for [Store Name] could not be allocated. Dispatch has been alerted."*
3. **Webhook Events:**
   - `RECURRING_PICKUP_TRIGGERED`
   - `RECURRING_PICKUP_COMPLETED`
   - `RECURRING_PICKUP_FAILED`

---

## 6. REST API Specification

| HTTP Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/merchants/me/recurring-pickups` | Lists all recurring schedules for the logged-in merchant | `merchant:api-access` |
| `POST` | `/api/merchants/me/recurring-pickups` | Creates a new recurring schedule | `merchant:api-access` |
| `GET` | `/api/merchants/me/recurring-pickups/{id}` | Retrieves a specific schedule and upcoming pickup dates | `merchant:api-access` |
| `PUT` | `/api/merchants/me/recurring-pickups/{id}` | Updates schedule parameters, frequency, or contact | `merchant:api-access` |
| `DELETE` | `/api/merchants/me/recurring-pickups/{id}` | Deletes or archives a schedule | `merchant:api-access` |
| `POST` | `/api/merchants/me/recurring-pickups/{id}/pause` | Temporarily suspends the schedule | `merchant:api-access` |
| `POST` | `/api/merchants/me/recurring-pickups/{id}/resume` | Resumes a paused schedule | `merchant:api-access` |
| `GET` | `/api/merchants/me/recurring-pickups/{id}/executions` | Returns past execution logs and generated tracking numbers | `merchant:api-access` |

---

## 7. Frontend User Experience & Workflow

### 1. Dedicated Portal View: `/merchant/recurring-pickups`
- Accessible via the Merchant Portal sidebar with a calendar/clock icon.
- Cards displaying each active schedule:
  - Title (e.g. "Morning Dispatch - Colombo Warehouse")
  - Recurrence badge: "Weekdays at 10:00 AM"
  - Vehicle required: Van / Motorcycle
  - Next scheduled pickup: "Tomorrow at 10:00 AM"
  - Quick Toggle: Pause / Resume

### 2. Creation Wizard
- Step 1: Frequency (Daily, Mon–Fri, Select days of week).
- Step 2: Pickup Time & Location (pre-fills from Merchant Business Profile).
- Step 3: Logistics Profile (average daily parcels, required vehicle class, special gate instructions).
- Step 4: Preview Calendar showing next 14 generated pickup dates.

---

## 8. Rollout Plan
1. **Phase 1 (Current):** Architectural proposal and team review.
2. **Phase 2:** Database migration `V15__merchant_recurring_pickups.sql` submitted to DBA team.
3. **Phase 3:** Dispatcher module integration test for auto-allocated courier manifests.
4. **Phase 4:** Frontend UI release on `/merchant/recurring-pickups`.
