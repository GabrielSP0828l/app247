RENAME TABLE app_cliente_config TO app_cliente_config_v15;

CREATE TABLE app_cliente_config (
    empresa_id CHAR(36) NOT NULL,
    catalogo BOOLEAN NOT NULL DEFAULT FALSE,
    promocoes BOOLEAN NOT NULL DEFAULT FALSE,
    fidelidade BOOLEAN NOT NULL DEFAULT FALSE,
    pontos BOOLEAN NOT NULL DEFAULT FALSE,
    cashback BOOLEAN NOT NULL DEFAULT FALSE,
    recompensas BOOLEAN NOT NULL DEFAULT FALSE,
    cupons BOOLEAN NOT NULL DEFAULT FALSE,
    qr_identificacao BOOLEAN NOT NULL DEFAULT FALSE,
    preferencias BOOLEAN NOT NULL DEFAULT FALSE,
    suporte BOOLEAN NOT NULL DEFAULT FALSE,
    loyalty_mode VARCHAR(20) NOT NULL DEFAULT 'NONE',
    points_per_currency DECIMAL(15,6) NULL,
    cashback_percent DECIMAL(9,6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (empresa_id),
    CONSTRAINT fk_app_config_empresa_p1b FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT ck_app_config_loyalty_mode_p1b
        CHECK (loyalty_mode IN ('NONE','POINTS','CASHBACK')),
    CONSTRAINT ck_app_config_loyalty_values_p1b CHECK (
        (loyalty_mode = 'NONE'
            AND fidelidade = FALSE
            AND points_per_currency IS NULL
            AND cashback_percent IS NULL)
        OR
        (loyalty_mode = 'POINTS'
            AND points_per_currency IS NOT NULL
            AND points_per_currency > 0
            AND cashback_percent IS NULL)
        OR
        (loyalty_mode = 'CASHBACK'
            AND points_per_currency IS NULL
            AND cashback_percent IS NOT NULL
            AND cashback_percent > 0
            AND cashback_percent <= 100)
    ),
    CONSTRAINT ck_app_config_derived_flags_p1b CHECK (
        (fidelidade = FALSE
            AND pontos = FALSE AND cashback = FALSE
            AND recompensas = FALSE AND cupons = FALSE)
        OR
        (fidelidade = TRUE AND loyalty_mode = 'POINTS'
            AND pontos = TRUE AND cashback = FALSE
            AND recompensas = TRUE AND cupons = TRUE)
        OR
        (fidelidade = TRUE AND loyalty_mode = 'CASHBACK'
            AND pontos = FALSE AND cashback = TRUE
            AND recompensas = FALSE AND cupons = FALSE)
    )
) ENGINE=InnoDB;

INSERT INTO app_cliente_config (
    empresa_id, catalogo, promocoes, fidelidade, pontos, cashback,
    recompensas, cupons, qr_identificacao, preferencias, suporte,
    loyalty_mode, points_per_currency, cashback_percent, created_at, updated_at
)
SELECT empresa_id,
       catalogo,
       promocoes,
       CASE WHEN fidelidade = TRUE AND configured_mode <> 'NONE' THEN TRUE ELSE FALSE END,
       CASE WHEN fidelidade = TRUE AND configured_mode = 'POINTS' THEN TRUE ELSE FALSE END,
       CASE WHEN fidelidade = TRUE AND configured_mode = 'CASHBACK' THEN TRUE ELSE FALSE END,
       CASE WHEN fidelidade = TRUE AND configured_mode = 'POINTS' THEN TRUE ELSE FALSE END,
       CASE WHEN fidelidade = TRUE AND configured_mode = 'POINTS' THEN TRUE ELSE FALSE END,
       qr_identificacao,
       preferencias,
       suporte,
       configured_mode,
       CASE WHEN configured_mode = 'POINTS' THEN points_per_currency ELSE NULL END,
       CASE WHEN configured_mode = 'CASHBACK' THEN cashback_percent ELSE NULL END,
       created_at,
       updated_at
FROM (
    SELECT empresa_id,
           MIN(catalogo) AS catalogo,
           MIN(promocoes) AS promocoes,
           MIN(fidelidade) AS fidelidade,
           MIN(qr_identificacao) AS qr_identificacao,
           MIN(preferencias) AS preferencias,
           MIN(suporte) AS suporte,
           CASE
               WHEN COUNT(DISTINCT loyalty_mode) = 1
                    AND MAX(loyalty_mode) = 'POINTS'
                    AND MIN(points_per_currency) > 0 THEN 'POINTS'
               WHEN COUNT(DISTINCT loyalty_mode) = 1
                    AND MAX(loyalty_mode) = 'CASHBACK'
                    AND MIN(cashback_percent) > 0
                    AND MAX(cashback_percent) <= 100 THEN 'CASHBACK'
               ELSE 'NONE'
           END AS configured_mode,
           MIN(points_per_currency) AS points_per_currency,
           MIN(cashback_percent) AS cashback_percent,
           MIN(updated_at) AS created_at,
           MAX(updated_at) AS updated_at
      FROM app_cliente_config_v15
     GROUP BY empresa_id
) consolidated;

DROP TABLE app_cliente_config_v15;

ALTER TABLE reward
    ADD COLUMN descricao VARCHAR(1000) NULL AFTER nome,
    ADD COLUMN validade_dias INT NULL AFTER validade,
    ADD COLUMN limite_uso_por_usuario INT NOT NULL DEFAULT 1 AFTER validade_dias,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

UPDATE reward
   SET tipo_desconto = CASE tipo_desconto
       WHEN 'PERCENTAGE' THEN 'PERCENTUAL'
       WHEN 'FIXED' THEN 'VALOR_FIXO'
       ELSE tipo_desconto END,
       validade_dias = GREATEST(1, LEAST(3650, CEIL(TIMESTAMPDIFF(SECOND, created_at, validade) / 86400))),
       updated_at = created_at;

UPDATE reward
   SET ativo = FALSE,
       custo_pontos = GREATEST(custo_pontos, 1),
       valor_minimo_compra = GREATEST(valor_minimo_compra, 0),
       valor_desconto = CASE
           WHEN valor_desconto <= 0 THEN 1
           WHEN tipo_desconto = 'PERCENTUAL' AND valor_desconto > 100 THEN 100
           ELSE valor_desconto END,
       desconto_maximo = CASE
           WHEN desconto_maximo IS NOT NULL AND desconto_maximo <= 0 THEN NULL
           ELSE desconto_maximo END
 WHERE custo_pontos <= 0
    OR valor_minimo_compra < 0
    OR valor_desconto <= 0
    OR (tipo_desconto = 'PERCENTUAL' AND valor_desconto > 100)
    OR (desconto_maximo IS NOT NULL AND desconto_maximo <= 0)
    OR tipo_desconto NOT IN ('PERCENTUAL','VALOR_FIXO');

ALTER TABLE reward
    MODIFY validade_dias INT NOT NULL,
    MODIFY updated_at DATETIME(6) NOT NULL,
    DROP CHECK ck_reward_discount_type,
    ADD CONSTRAINT ck_reward_discount_type_p1b
        CHECK (tipo_desconto IN ('PERCENTUAL','VALOR_FIXO')),
    ADD CONSTRAINT ck_reward_financial_p1b CHECK (
        custo_pontos > 0
        AND valor_minimo_compra >= 0
        AND valor_desconto > 0
        AND (tipo_desconto <> 'PERCENTUAL' OR valor_desconto <= 100)
        AND (desconto_maximo IS NULL OR desconto_maximo > 0)
        AND validade_dias BETWEEN 1 AND 3650
        AND limite_uso_por_usuario BETWEEN 1 AND 1000000
    );

CREATE INDEX idx_reward_empresa_ativo_validade
    ON reward (empresa_id, ativo, validade, created_at);

UPDATE customer_coupon
   SET tipo = CASE tipo
       WHEN 'PERCENTAGE' THEN 'PERCENTUAL'
       WHEN 'FIXED' THEN 'VALOR_FIXO'
       ELSE tipo END;

UPDATE customer_coupon
   SET status = 'CANCELLED',
       tipo = CASE WHEN tipo IN ('PERCENTUAL','VALOR_FIXO') THEN tipo ELSE 'VALOR_FIXO' END,
       valor_minimo_compra = GREATEST(valor_minimo_compra, 0),
       valor = CASE
           WHEN valor <= 0 THEN 1
           WHEN tipo = 'PERCENTUAL' AND valor > 100 THEN 100
           ELSE valor END,
       desconto_maximo = CASE
           WHEN desconto_maximo IS NOT NULL AND desconto_maximo <= 0 THEN NULL
           ELSE desconto_maximo END
 WHERE valor <= 0
    OR valor_minimo_compra < 0
    OR (tipo = 'PERCENTUAL' AND valor > 100)
    OR (desconto_maximo IS NOT NULL AND desconto_maximo <= 0)
    OR tipo NOT IN ('PERCENTUAL','VALOR_FIXO');

UPDATE customer_coupon c
JOIN (
    SELECT id
      FROM (
          SELECT id,
                 ROW_NUMBER() OVER (
                     PARTITION BY reserved_order_id
                     ORDER BY (status = 'USED') DESC, (status = 'RESERVED') DESC,
                              created_at, id
                 ) AS row_number_in_order
            FROM customer_coupon
           WHERE reserved_order_id IS NOT NULL
      ) ranked
     WHERE row_number_in_order > 1
) duplicate_coupon ON duplicate_coupon.id = c.id
   SET c.reserved_order_id = NULL,
       c.status = CASE WHEN c.status = 'RESERVED' THEN 'AVAILABLE' ELSE c.status END;

ALTER TABLE customer_coupon
    DROP FOREIGN KEY fk_coupon_reserved_order,
    ADD CONSTRAINT fk_coupon_reserved_order_tenant_p1b
        FOREIGN KEY (reserved_order_id, empresa_id) REFERENCES orders (id, empresa_id),
    ADD CONSTRAINT uk_coupon_reserved_order_p1b UNIQUE (reserved_order_id),
    ADD CONSTRAINT ck_coupon_financial_p1b CHECK (
        tipo IN ('PERCENTUAL','VALOR_FIXO')
        AND valor > 0
        AND (tipo <> 'PERCENTUAL' OR valor <= 100)
        AND valor_minimo_compra >= 0
        AND (desconto_maximo IS NULL OR desconto_maximo > 0)
    );

ALTER TABLE loyalty_account
    ADD CONSTRAINT uk_loyalty_account_id_empresa_p1b UNIQUE (id, empresa_id),
    ADD CONSTRAINT ck_loyalty_account_balances_p1b
        CHECK (points_balance >= 0 AND cashback_balance >= 0);

ALTER TABLE loyalty_movement
    ADD COLUMN empresa_id CHAR(36) NULL AFTER id;

UPDATE loyalty_movement movement
JOIN loyalty_account account ON account.id = movement.account_id
   SET movement.empresa_id = account.empresa_id;

UPDATE loyalty_movement
   SET points_delta = CASE
           WHEN points_delta IS NULL AND cashback_delta IS NULL THEN 0
           ELSE points_delta END,
       cashback_delta = CASE
           WHEN points_delta IS NOT NULL AND cashback_delta IS NOT NULL THEN NULL
           ELSE cashback_delta END;

ALTER TABLE loyalty_movement
    MODIFY empresa_id CHAR(36) NOT NULL,
    DROP FOREIGN KEY fk_loyalty_movement_account,
    DROP FOREIGN KEY fk_loyalty_movement_order,
    ADD CONSTRAINT fk_loyalty_movement_account_tenant_p1b
        FOREIGN KEY (account_id, empresa_id) REFERENCES loyalty_account (id, empresa_id),
    ADD CONSTRAINT fk_loyalty_movement_order_tenant_p1b
        FOREIGN KEY (order_id, empresa_id) REFERENCES orders (id, empresa_id),
    ADD CONSTRAINT ck_loyalty_movement_delta_p1b CHECK (
        (points_delta IS NOT NULL AND cashback_delta IS NULL)
        OR (points_delta IS NULL AND cashback_delta IS NOT NULL)
    );

ALTER TABLE reward_redemption
    ADD CONSTRAINT uk_redemption_coupon_p1b UNIQUE (coupon_id),
    ADD CONSTRAINT ck_redemption_points_p1b CHECK (points_spent > 0);
