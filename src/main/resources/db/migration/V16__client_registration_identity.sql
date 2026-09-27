UPDATE users
SET cpf = REPLACE(REPLACE(REPLACE(REPLACE(cpf, '.', ''), '-', ''), ' ', ''), '/', ''),
    email = LOWER(TRIM(email));

ALTER TABLE users
    MODIFY empresa_id CHAR(36) NULL,
    MODIFY cpf VARCHAR(11) NOT NULL,
    ADD COLUMN cpf_verified_at DATETIME(6) NULL AFTER email_verificado,
    ADD COLUMN terms_version VARCHAR(80) NULL AFTER cpf_verified_at,
    ADD COLUMN privacy_version VARCHAR(80) NULL AFTER terms_version,
    ADD COLUMN terms_accepted_at DATETIME(6) NULL AFTER privacy_version,
    ADD COLUMN privacy_accepted_at DATETIME(6) NULL AFTER terms_accepted_at;

CREATE TABLE customer_identity (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    cpf_hash CHAR(64) NOT NULL,
    claimed_user_id CHAR(36) NULL,
    claimed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_identity_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_customer_identity_empresa_cpf UNIQUE (empresa_id, cpf_hash),
    CONSTRAINT fk_customer_identity_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_customer_identity_user_tenant FOREIGN KEY (claimed_user_id, empresa_id)
        REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE INDEX idx_customer_identity_claimed_user ON customer_identity (claimed_user_id, claimed_at);
CREATE INDEX idx_customer_identity_hash ON customer_identity (cpf_hash);

ALTER TABLE carrinho
    ADD COLUMN customer_identity_id CHAR(36) NULL AFTER terminal_id,
    ADD CONSTRAINT fk_carrinho_customer_identity_tenant
        FOREIGN KEY (customer_identity_id, empresa_id)
        REFERENCES customer_identity (id, empresa_id);

CREATE INDEX idx_carrinho_customer_identity ON carrinho (customer_identity_id, created_at);

ALTER TABLE orders
    ADD COLUMN customer_identity_id CHAR(36) NULL AFTER user_id,
    ADD CONSTRAINT fk_orders_customer_identity_tenant
        FOREIGN KEY (customer_identity_id, empresa_id)
        REFERENCES customer_identity (id, empresa_id);

CREATE INDEX idx_orders_customer_identity_created ON orders (customer_identity_id, created_at);
