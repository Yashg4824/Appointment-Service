-- A deterministic key is stable across retries and independent of the generated reminder id.
ALTER TABLE reminders ADD COLUMN idempotency_key VARCHAR(100);

UPDATE reminders
SET idempotency_key = appointment_id || '-' || reminder_type
WHERE idempotency_key IS NULL;

ALTER TABLE reminders ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE reminders
    ADD CONSTRAINT uq_reminders_idempotency_key UNIQUE (idempotency_key);
