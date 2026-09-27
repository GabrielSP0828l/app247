# Coordenação entre cupom e pagamento

## Estados operacionais

O cupom segue:

```text
AVAILABLE -> RESERVED -> USED
                  |
                  +-> AVAILABLE ou EXPIRED
```

O QR apenas identifica o cupom. A aplicação usa lock pessimista, valida Empresa,
Condomínio/Order e titular, calcula a base pós-promoções e reserva o cupom para uma
Order ainda editável. `customer_coupon.reserved_order_id` é único e `lock_version`
protege atualizações concorrentes. `orders.coupon_id` registra o cupom efetivamente
incluído no snapshot; o FK composto com `empresa_id` impede vínculo cross-tenant.

Uma Order aceita no máximo um cupom. Depois de `price_finalized_at`, o Terminal não
pode trocar ou reaplicar cupom.

## Valor mínimo e máximo

O mínimo usa `subtotal_pos_promocoes`, antes do desconto do próprio cupom. Percentual
é limitado a 100% e respeita `desconto_maximo` quando presente. Desconto fixo também
nunca ultrapassa a base.

## Uso e liberação

- `PROCESSED`: `RESERVED -> USED`, mantendo a referência histórica à Order;
- `PENDING`, `ACTION_REQUIRED`, `UNKNOWN` ou `CANCELLING`: permanece `RESERVED`;
- falha definitiva, expiração ou cancelamento confirmado antes da aprovação:
  libera para `AVAILABLE`, ou `EXPIRED` se a validade terminou;
- aprovação tardia confirmada vence a intenção de cancelamento e consome o cupom.

Os listeners participam da transação da transição financeira e os repositórios
carregam a reserva com lock. Não há liberação por timeout.

## Snapshot da recompensa

`customer_coupon` já conserva tipo, valor, compra mínima, desconto máximo, emissão e
expiração recebidos no resgate. Editar a Recompensa depois não altera o cupom emitido.
A Order usa esse snapshot do cupom; não consulta a Recompensa atual para reconstruir
uma compra antiga.
