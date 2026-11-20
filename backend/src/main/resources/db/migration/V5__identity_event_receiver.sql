-- Identity events (auth step 6, receiver 6f): signed SETs keep app_users correct within seconds.

-- The newest event version applied for each person; an event with a version not higher than this is old and ignored
ALTER TABLE app_users ADD COLUMN last_event_version BIGINT NOT NULL DEFAULT 0;

-- Events already processed (by their jti), so a repeat changes nothing. Rows older than 7 days are deleted:
-- auth stops retrying after 24 h, and the version check above still guards anything older.
CREATE TABLE processed_identity_events (
    jti          VARCHAR(200)             PRIMARY KEY,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_identity_events_received ON processed_identity_events (received_at);
