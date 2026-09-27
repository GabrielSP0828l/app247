CREATE TABLE app_cliente_config (
    condominio_id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    catalogo BOOLEAN NOT NULL DEFAULT TRUE,
    promocoes BOOLEAN NOT NULL DEFAULT TRUE,
    fidelidade BOOLEAN NOT NULL DEFAULT TRUE,
    pontos BOOLEAN NOT NULL DEFAULT TRUE,
    cashback BOOLEAN NOT NULL DEFAULT FALSE,
    recompensas BOOLEAN NOT NULL DEFAULT TRUE,
    cupons BOOLEAN NOT NULL DEFAULT TRUE,
    qr_identificacao BOOLEAN NOT NULL DEFAULT TRUE,
    preferencias BOOLEAN NOT NULL DEFAULT TRUE,
    suporte BOOLEAN NOT NULL DEFAULT TRUE,
    loyalty_mode VARCHAR(20) NOT NULL DEFAULT 'POINTS',
    points_per_currency DECIMAL(15,6) NOT NULL DEFAULT 1.000000,
    cashback_percent DECIMAL(9,6) NOT NULL DEFAULT 0.000000,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (condominio_id),
    CONSTRAINT fk_app_config_condominio_tenant FOREIGN KEY (condominio_id, empresa_id)
        REFERENCES condominio (id, empresa_id),
    CONSTRAINT ck_app_config_loyalty_mode CHECK (loyalty_mode IN ('NONE','POINTS','CASHBACK'))
) ENGINE=InnoDB;

CREATE TABLE loyalty_account (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    points_balance BIGINT NOT NULL DEFAULT 0,
    cashback_balance DECIMAL(15,6) NOT NULL DEFAULT 0.000000,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_loyalty_user UNIQUE (user_id),
    CONSTRAINT fk_loyalty_user_tenant FOREIGN KEY (user_id, empresa_id) REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE TABLE loyalty_movement (
    id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    order_id CHAR(36) NULL,
    type VARCHAR(30) NOT NULL,
    points_delta BIGINT NULL,
    cashback_delta DECIMAL(15,6) NULL,
    description VARCHAR(240) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_loyalty_movement_key UNIQUE (idempotency_key),
    CONSTRAINT fk_loyalty_movement_account FOREIGN KEY (account_id) REFERENCES loyalty_account (id),
    CONSTRAINT fk_loyalty_movement_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB;

CREATE TABLE reward (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NULL,
    nome VARCHAR(150) NOT NULL,
    tipo_desconto VARCHAR(30) NOT NULL,
    valor_desconto DECIMAL(15,6) NOT NULL,
    custo_pontos BIGINT NOT NULL,
    valor_minimo_compra DECIMAL(15,6) NOT NULL DEFAULT 0.000000,
    desconto_maximo DECIMAL(15,6) NULL,
    validade DATETIME(6) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reward_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT fk_reward_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_reward_condominio_tenant FOREIGN KEY (condominio_id, empresa_id) REFERENCES condominio (id, empresa_id),
    CONSTRAINT ck_reward_discount_type CHECK (tipo_desconto IN ('PERCENTAGE','FIXED'))
) ENGINE=InnoDB;

CREATE TABLE customer_coupon (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    reward_id CHAR(36) NULL,
    nome VARCHAR(150) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    valor DECIMAL(15,6) NOT NULL,
    valor_minimo_compra DECIMAL(15,6) NOT NULL DEFAULT 0.000000,
    desconto_maximo DECIMAL(15,6) NULL,
    validade DATETIME(6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reserved_order_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupon_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT fk_coupon_user_tenant FOREIGN KEY (user_id, empresa_id) REFERENCES users (id, empresa_id),
    CONSTRAINT fk_coupon_reward_tenant FOREIGN KEY (reward_id, empresa_id) REFERENCES reward (id, empresa_id),
    CONSTRAINT fk_coupon_reserved_order FOREIGN KEY (reserved_order_id) REFERENCES orders (id),
    CONSTRAINT ck_coupon_status CHECK (status IN ('AVAILABLE','RESERVED','USED','EXPIRED','CANCELLED'))
) ENGINE=InnoDB;
CREATE INDEX idx_coupon_user_status ON customer_coupon (user_id, status, validade);

CREATE TABLE reward_redemption (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    reward_id CHAR(36) NOT NULL,
    coupon_id CHAR(36) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    points_spent BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_redemption_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_redemption_user_tenant FOREIGN KEY (user_id, empresa_id) REFERENCES users (id, empresa_id),
    CONSTRAINT fk_redemption_reward_tenant FOREIGN KEY (reward_id, empresa_id) REFERENCES reward (id, empresa_id),
    CONSTRAINT fk_redemption_coupon_tenant FOREIGN KEY (coupon_id, empresa_id) REFERENCES customer_coupon (id, empresa_id)
) ENGINE=InnoDB;

CREATE TABLE app_cliente_preference (
    user_id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    auto_email BOOLEAN NOT NULL DEFAULT FALSE,
    auto_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_app_preference_user_tenant FOREIGN KEY (user_id, empresa_id) REFERENCES users (id, empresa_id)
) ENGINE=InnoDB;

CREATE TABLE purchase_support (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    protocolo VARCHAR(40) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    mensagem VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_support_protocol UNIQUE (protocolo),
    CONSTRAINT fk_purchase_support_user_tenant FOREIGN KEY (user_id, empresa_id) REFERENCES users (id, empresa_id),
    CONSTRAINT fk_purchase_support_order_tenant FOREIGN KEY (order_id, empresa_id) REFERENCES orders (id, empresa_id)
) ENGINE=InnoDB;
