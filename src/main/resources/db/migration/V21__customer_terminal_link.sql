ALTER TABLE carrinho
    ADD COLUMN user_id CHAR(36) NULL AFTER terminal_id,
    ADD CONSTRAINT fk_carrinho_user_tenant FOREIGN KEY (user_id, empresa_id)
        REFERENCES users (id, empresa_id);

CREATE INDEX idx_carrinho_user_created ON carrinho (user_id, created_at);

CREATE TABLE customer_identification_token (
    id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    user_id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    consumed_cart_id CHAR(36) NULL,
    invalidated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_identification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_customer_token_user_tenant FOREIGN KEY (user_id, empresa_id)
        REFERENCES users (id, empresa_id),
    CONSTRAINT fk_customer_token_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id),
    CONSTRAINT fk_customer_token_consumed_cart FOREIGN KEY (consumed_cart_id, empresa_id)
        REFERENCES carrinho (id, empresa_id),
    CONSTRAINT ck_customer_token_consumption CHECK (
        (consumed_at IS NULL AND consumed_cart_id IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_cart_id IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE INDEX idx_customer_token_user_active
    ON customer_identification_token (user_id, consumed_at, invalidated_at, expires_at);
CREATE INDEX idx_customer_token_expiration
    ON customer_identification_token (expires_at, consumed_at, invalidated_at);
