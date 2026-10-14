ALTER TABLE terminal
    ADD COLUMN device_credential_hash CHAR(64) NULL AFTER reset_reason,
    ADD COLUMN device_credential_version INT NULL AFTER device_credential_hash,
    ADD COLUMN device_credential_created_at DATETIME(6) NULL AFTER device_credential_version,
    ADD COLUMN device_credential_revoked_at DATETIME(6) NULL AFTER device_credential_created_at,
    ADD CONSTRAINT uk_terminal_device_credential_hash UNIQUE (device_credential_hash),
    ADD CONSTRAINT ck_terminal_device_credential_version CHECK (
        (device_credential_hash IS NULL AND device_credential_version IS NULL)
        OR (device_credential_hash IS NOT NULL AND device_credential_version >= 1)
    );

CREATE INDEX idx_terminal_device_credential_state
    ON terminal (device_credential_revoked_at, ativo);
