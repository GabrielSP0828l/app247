ALTER TABLE orders
    ADD COLUMN subtotal_pos_promocoes DECIMAL(15,6) NULL AFTER subtotal,
    ADD COLUMN desconto_promocoes DECIMAL(15,6) NULL AFTER subtotal_pos_promocoes,
    ADD COLUMN desconto_cupom DECIMAL(15,6) NULL AFTER desconto_promocoes,
    ADD COLUMN coupon_id CHAR(36) NULL AFTER total_cobrado,
    ADD COLUMN price_finalized_at DATETIME(6) NULL AFTER coupon_id;

UPDATE orders o
JOIN (
    SELECT order_id,
           SUM(preco_original * quantidade) AS subtotal_original,
           SUM(subtotal_calculado) AS subtotal_pos_promocoes
      FROM order_item
     GROUP BY order_id
) item_totals ON item_totals.order_id = o.id
   SET o.subtotal = ROUND(item_totals.subtotal_original, 6),
       o.subtotal_pos_promocoes = ROUND(item_totals.subtotal_pos_promocoes, 6),
       o.desconto_promocoes = ROUND(
           item_totals.subtotal_original - item_totals.subtotal_pos_promocoes, 6),
       o.desconto_cupom = ROUND(GREATEST(
           item_totals.subtotal_pos_promocoes - o.total_calculado, 0), 6),
       o.desconto = ROUND(
           item_totals.subtotal_original - o.total_calculado, 6),
       o.total_cobrado = ROUND(o.total_calculado, 2);

UPDATE orders o
LEFT JOIN (
    SELECT reserved_order_id, MIN(id) AS coupon_id
      FROM customer_coupon
     WHERE reserved_order_id IS NOT NULL
     GROUP BY reserved_order_id
) reserved_coupon ON reserved_coupon.reserved_order_id = o.id
LEFT JOIN (
    SELECT order_id, MIN(created_at) AS first_attempt_at
      FROM payment_attempt
     GROUP BY order_id
) first_attempt ON first_attempt.order_id = o.id
   SET o.coupon_id = reserved_coupon.coupon_id,
       o.price_finalized_at = first_attempt.first_attempt_at;

ALTER TABLE orders
    MODIFY subtotal_pos_promocoes DECIMAL(15,6) NOT NULL,
    MODIFY desconto_promocoes DECIMAL(15,6) NOT NULL,
    MODIFY desconto_cupom DECIMAL(15,6) NOT NULL,
    ADD CONSTRAINT fk_orders_coupon_tenant_p1c
        FOREIGN KEY (coupon_id, empresa_id) REFERENCES customer_coupon (id, empresa_id),
    ADD CONSTRAINT ck_orders_financial_snapshot_p1c CHECK (
        subtotal > 0
        AND subtotal_pos_promocoes > 0
        AND total_calculado > 0
        AND total_cobrado > 0
        AND subtotal >= subtotal_pos_promocoes
        AND subtotal_pos_promocoes >= total_calculado
        AND desconto_promocoes = ROUND(subtotal - subtotal_pos_promocoes, 6)
        AND desconto_cupom = ROUND(subtotal_pos_promocoes - total_calculado, 6)
        AND desconto = ROUND(desconto_promocoes + desconto_cupom, 6)
        AND total_cobrado = ROUND(total_calculado, 2)
    );

CREATE INDEX idx_orders_coupon ON orders (coupon_id);
CREATE INDEX idx_orders_price_finalized ON orders (price_finalized_at, status);

ALTER TABLE customer_coupon
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

CREATE INDEX idx_coupon_status_reserved_order
    ON customer_coupon (status, reserved_order_id, validade);

UPDATE payment_attempt p
JOIN (
    SELECT id
      FROM (
          SELECT id,
                 ROW_NUMBER() OVER (
                     PARTITION BY order_id
                     ORDER BY attempt_number DESC, created_at DESC, id DESC
                 ) AS active_rank
            FROM payment_attempt
           WHERE status IN ('PENDING','ACTION_REQUIRED','UNKNOWN','CANCELLING')
      ) ranked_active
     WHERE active_rank > 1
) duplicate_active ON duplicate_active.id = p.id
   SET p.status = 'FAILED',
       p.status_detail = 'superseded_by_v19_active_attempt_constraint',
       p.finished_at = COALESCE(p.finished_at, CURRENT_TIMESTAMP(6)),
       p.updated_at = CURRENT_TIMESTAMP(6);

ALTER TABLE payment_attempt
    ADD COLUMN active_order_id CHAR(36)
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('PENDING','ACTION_REQUIRED','UNKNOWN','CANCELLING')
                THEN order_id
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT uk_payment_attempt_one_active_p1c UNIQUE (active_order_id),
    ADD CONSTRAINT ck_payment_attempt_status_p1c CHECK (
        status IN ('PENDING','ACTION_REQUIRED','UNKNOWN','CANCELLING',
                   'PROCESSED','FAILED','CANCELED','EXPIRED','REFUNDED')
    );

CREATE INDEX idx_payment_provider_order_lookup
    ON payment_attempt (provider_order_id, status);
