ALTER TABLE payment_attempt
    ADD COLUMN refund_requested_at DATETIME(6) NULL AFTER cancellation_idempotency_key,
    ADD COLUMN refund_idempotency_key VARCHAR(120) NULL AFTER refund_requested_at,
    ADD COLUMN refund_confirmed_at DATETIME(6) NULL AFTER refund_idempotency_key,
    ADD COLUMN provider_refund_id VARCHAR(120) NULL AFTER refund_confirmed_at,
    ADD CONSTRAINT uk_payment_refund_idempotency UNIQUE (refund_idempotency_key);

CREATE INDEX idx_payment_refund_pending
    ON payment_attempt (refund_requested_at, refund_confirmed_at, status);
