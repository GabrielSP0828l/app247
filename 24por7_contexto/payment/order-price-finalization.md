# Fechamento autoritativo do preço da Order

## Fonte de verdade

O Terminal envia Produto, quantidade e, quando aplicável, uma expectativa de preço.
O backend busca o Produto no tenant do Terminal e calcula o preço com
`PricingService`. `expectedUnitPrice` apenas detecta aumento e pode produzir
`409 PRICE_CHANGED`; nunca define o valor cobrado.

A composição financeira tem uma única ordem:

1. preço original dos itens;
2. promoções automáticas vigentes;
3. subtotal pós-promoções;
4. validação e reserva de no máximo um cupom;
5. desconto do cupom;
6. total preciso e total cobrado arredondado.

`OrderPriceFinalizationService` centraliza essa composição. Todos os cálculos usam
`BigDecimal`; `MoneyPolicy` normaliza persistência em seis casas e o valor enviado ao
provedor em duas casas.

## Snapshot

`order_item` conserva produto, SKU, nome, unidade, quantidade, preço original,
preço aplicado, promoção, desconto e subtotal da linha. `orders` conserva:

- `subtotal`: soma dos preços originais;
- `subtotal_pos_promocoes`;
- `desconto_promocoes`;
- `desconto_cupom`;
- `desconto`: soma dos descontos;
- `total_calculado` em precisão interna;
- `total_cobrado` arredondado;
- `coupon_id`, quando aplicável;
- `price_finalized_at`.

A V19 adiciona um `CHECK` que exige a identidade entre esses valores. O
`PaymentAttempt.amount_requested` deve ser exatamente igual a `orders.total_cobrado`.
`MercadoPagoCobrancaService` envia o valor da tentativa persistida e recusa divergência
com o snapshot da Order.

## Imutabilidade

Antes do primeiro attempt, `finalizeForPayment` valida o snapshot, a reserva do cupom e
muda o carrinho de `READY_FOR_PAYMENT` para `PAYMENT_PENDING`. A partir daí não existe
reprecificação, troca de cupom ou mutação de itens no fluxo de pagamento. Retry
financeiro reutiliza o snapshot congelado e cria outro `PaymentAttempt`.

Alterações posteriores em Produto, Promoção ou Recompensa não modificam a Order. Uma
Order histórica anterior à V19 pode ser lida, mas não é cobrável sem
`price_finalized_at` e snapshot válido.

## Exemplos validados

- base pós-promoções `120,00`, cupom de `10%`, mínimo `100,00`, máximo `25,00`:
  desconto `12,00`, total `108,00`;
- base `80,00`, mínimo `100,00`: `COUPON_MINIMUM_NOT_REACHED`, com
  `missingAmount=20,00`;
- base `400,00`, `10%`, máximo `25,00`: desconto limitado a `25,00`.
