-- The event outbox (Spring Modulith "event publication registry").
-- When a module publishes an event inside a transaction, one row per listener is written in the SAME transaction.
-- The row is completed when the listener succeeds. A crash or a failed listener leaves it incomplete, and it is
-- sent again later, so "status changed" can't be lost between the database and RabbitMQ.
-- Layout = Spring Modulith 2.x schema v2. Plain indexes so the same file runs on Postgres and the H2 test database.
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);

-- "What is still open?" is the common question (completed rows are deleted, see application.properties)
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
