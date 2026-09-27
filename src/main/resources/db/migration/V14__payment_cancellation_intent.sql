ALTER TABLE payment_attempt
    ADD COLUMN cancellation_requested_at TIMESTAMP NULL,
    ADD COLUMN cancellation_idempotency_key VARCHAR(120) NULL;

CREATE INDEX idx_payment_attempt_cancellation_requested
    ON payment_attempt (cancellation_requested_at);
