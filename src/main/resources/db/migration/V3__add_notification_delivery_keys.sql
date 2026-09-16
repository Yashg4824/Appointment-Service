-- Durable deduplication for the logging sender. A real provider should enforce the same
-- idempotency key at its boundary; this table only makes the local stub crash/restart safe.
CREATE TABLE notification_deliveries (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    recorded_at     TIMESTAMPTZ NOT NULL
);
