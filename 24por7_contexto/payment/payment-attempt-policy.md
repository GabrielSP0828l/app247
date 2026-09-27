# Política de PaymentAttempt

## Cardinalidade e criação

Uma Order possui `0..N PaymentAttempt`. Cada linha é uma tentativa financeira e
conserva `attempt_number`, valor, provider, channel, status, chave idempotente,
referência externa, identificadores do provedor e timestamps.

O primeiro pagamento cria o attempt 1. Retry financeiro após `FAILED`, `CANCELED` ou
`EXPIRED` cria o próximo número; uma tentativa encerrada nunca é reutilizada. O valor
de cada attempt é o `total_cobrado` congelado da Order.

## Persistência antes do provedor

`PointPaymentPersistenceService.prepare`, em `REQUIRES_NEW`, bloqueia e valida
Carrinho, Empresa, Terminal, Order, preço e cupom, persiste o attempt, chave
idempotente e referência externa e faz commit. Somente então
`PointPaymentSubmissionService` chama o Mercado Pago, fora da transação longa. A
resposta remota é persistida em outra transação.

A chave de idempotência pertence à tentativa e permanece estável. Reenvio técnico por
timeout usa o mesmo attempt e a mesma chave; não é retry financeiro.

## Estados

Estados não terminais que bloqueiam novo attempt:

- `PENDING`;
- `ACTION_REQUIRED` (equivalente operacional a processamento/ação no Point);
- `UNKNOWN`;
- `CANCELLING`.

Estados que permitem novo attempt financeiro: `FAILED`, `CANCELED` e `EXPIRED`.
`PROCESSED` é aprovação e não permite retry. `REFUNDED` também não permite nova
tentativa da mesma Order.

Timeout ou indisponibilidade depois do início do POST vira `UNKNOWN`, nunca
`FAILED`. Enquanto não houver resultado terminal, a Order e o cupom continuam
bloqueados.

## Transições e efeitos

`PaymentStateTransitionService` resolve o attempt pela referência externa, registra o
evento de provider e aplica transições monotônicas. `PROCESSED` não regride para
pendente, cancelado ou rejeitado. Aprovação confirmada vence uma intenção local de
cancelamento. Webhook, status query e scheduler usam a mesma transição.

`OrderPaidEvent` só é emitido quando a Order muda efetivamente para `PROCESSED`.
Evento duplicado ou segunda confirmação idêntica não volta a usar o cupom nem repete
efeitos pós-pagamento.
