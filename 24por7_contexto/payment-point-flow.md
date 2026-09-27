# Fluxo de pagamento Mercado Pago Point

Voltar para [[00-index]]. Contrato HTTP em [[api]], reconciliação em [[payment-reconciliation]] e eventos em [[websocket]].

## Máquina de estados

```text
CONFIRMAR E PAGAR
  -> PAYMENT_CREATING (loading "Enviando pagamento")
  -> resposta Spring com orderId + remoteOrderId
  -> WAITING_POINT (tela laranja)
       -> PENDING / CREATED / AT_TERMINAL / ACTION_REQUIRED: permanece laranja
       -> PROCESSING/WAITING_PAYMENT no contrato do Terminal: permanece laranja
       -> PROCESSED: tela aprovada
       -> FAILED/CANCELED/EXPIRED: estado terminal
       -> CANCELAR COMPRA: CANCELLING + reconciliação
```

`PointPaymentSubmissionService` termina depois que o Mercado Pago aceita a criação e `PointPaymentPersistenceService` persiste `providerOrderId`; ele não espera cartão, senha ou aprovação. Por isso a resposta inicial já encerra o loading. WebSocket acelera a mudança posterior, mas polling HTTP e recuperação por Terminal são obrigatórios.

## Cancelamento de método não é cancelamento da Order

Cancelar Débito, Crédito ou outra opção na interface física da Point pode devolver a maquininha ao começo da mesma cobrança. O backend não tenta inferir essa subtela. Enquanto o estado remoto for não terminal, a Order e a `PaymentAttempt` continuam ativas, o Terminal permanece laranja e o cliente pode pressionar verde novamente para escolher outra forma. Nenhuma segunda Order é criada.

## Cancelamento integral

`POST /pagamento/terminal/order/{orderId}/cancelamento?terminalId={uuid}` valida a associação da Order ao Terminal e executa:

1. lock pessimista na `PaymentAttempt`;
2. persistência de `cancellation_requested_at` e `cancellation_idempotency_key`;
3. reconciliação antes do comando remoto; se já estiver `PROCESSED`, aprovação vence;
4. `POST /v1/orders/{remoteOrderId}/cancel` com Bearer da Empresa e a chave persistida;
5. aplicação da resposta pela máquina monotônica comum;
6. nova reconciliação se houver timeout, conflito ou indisponibilidade.

Falha não inventa `CANCELED`. A resposta mantém `cancellationRequested=true`, a tentativa bloqueada e o Terminal em “Cancelando compra”. O comando é repetível sem gerar múltiplos cancelamentos.

A API pública atual do Mercado Pago aceita cancelamento por API apenas em `created`. Para `at_terminal`, a operação precisa ser cancelada na maquininha. O Terminal instrui essa ação e continua consultando até o backend confirmar estado terminal; ele nunca limpa localmente por causa dessa limitação.

## Abandono, timeout e recuperação

O deadline existente do checkout continua em 10 minutos; não foi criado um terceiro timer concorrente. Depois do aceite remoto, o recheck visual de 90 segundos apenas reforça a instrução e consulta status. Ao expirar o checkout com Order ativa, o Terminal inicia o mesmo cancelamento integral; não abre Home. Timeout do cancelamento mantém `CANCELLING` e polling.

`active_payment.cancellation_requested` preserva a intenção local. No backend, a
migration V14 preserva a intenção autoritativa e a V19 admite `CANCELLING` na barreira
de tentativa ativa. Se o Terminal ou WebSocket reiniciar,
`GET /pagamento/terminal/{terminalId}/ativo` recupera a tentativa, reconcilia e devolve
`remoteOrderId`/`cancellationRequested`/`attemptStatus`. Uma tentativa sem ID remoto e
já marcada para cancelamento não é reenviada automaticamente.

## Proteção contra segunda cobrança

O Terminal bloqueia `begin_payment`, redireciona qualquer tentativa de entrar no
carrinho para a recuperação e correlaciona callbacks por `orderId/paymentAttemptId`.
O backend mantém a barreira definitiva: lock do Terminal, busca por Order não resolvida
e unique da tentativa ativa. Se outra compra estiver ativa, responde
`PAYMENT_ALREADY_ACTIVE` com os IDs de recuperação. `UNKNOWN` e `CANCELLING` bloqueiam;
`FAILED`, `CANCELED` e `EXPIRED` permitem novo attempt da mesma Order.

## Respostas e precedência

- `PROCESSED/APPROVED` é monotônico e vence uma solicitação concorrente de cancelamento;
- `CANCELED/CANCELLED` só encerra a experiência após confirmação backend/remota;
- `EXPIRED` é terminal e pode concluir um cancelamento por abandono;
- timeout, HTTP 5xx e status desconhecido mantêm bloqueio;
- eventos de outra Order/tentativa são ignorados pelo Terminal.

Nenhum teste automatizado inicia cobrança financeira real.
