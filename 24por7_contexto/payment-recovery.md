# Recuperação segura de pagamento Point

Voltar para [[00-index]]. Implementação backend em [[payment-reconciliation]], integração em [[mercado-pago]] e contratos em [[api]].

## Invariante

> Se existe uma tentativa de pagamento não resolvida, primeiro descubra o estado real dela. Somente depois permita uma nova tentativa.

Timeout HTTP, Point desligada, backend offline, WebSocket desconectado e resposta desconhecida não são rejeição. São estados conservadores: a tentativa permanece ativa e deve ser reconciliada. Uma tentativa só libera outra compra quando o backend confirma `FAILED/REJECTED`, `CANCELED`, `EXPIRED` ou outro estado terminal não aprovado. `PROCESSED/APPROVED` segue para o pós-compra e nunca permite retry da mesma Order.

## Máquina de estados real

```text
PaymentAttempt local persistida
          |
          v
 Order PENDING -- POST remoto com idempotencyKey persistida --+
      |                                                    |
      | timeout/queda                                      | resposta
      v                                                    v
 UNKNOWN sem providerOrderId                         CREATED / AT_TERMINAL
      |                                                    |
      +--------------- RECONCILIAÇÃO ----------------------+
                               |
              +----------------+----------------+
              v                v                v
          PROCESSED          FAILED      CANCELED / EXPIRED
          (APPROVED)        (REJECTED)        (retry liberado)
```

`ACTION_REQUIRED` continua intermediário. `UNKNOWN` é um estado persistido de
`PaymentAttempt`; a Order permanece `PENDING` enquanto o resultado remoto é incerto.
`PaymentStateTransitionService` aplica webhook, status query e resposta idempotente
tardia, com lock, versão e transições monotônicas.

## Início e concorrência

1. `PointPaymentPersistenceService.prepare` bloqueia carrinho e Terminal.
2. Rejeita outra Order intermediária do mesmo Terminal com `409 PAYMENT_ALREADY_ACTIVE` e IDs de recuperação.
3. Cria/reutiliza Order e persiste `PaymentAttempt PENDING`, valor congelado,
   `externalReference` e `idempotencyKey`.
4. Faz commit.
5. `PointPaymentSubmissionService` marca o instante da submissão e chama o Mercado Pago fora da transação.
6. Persiste `providerOrderId`, transaction ID e metadados antes de aplicar o estado remoto.

O lock por Order evita dois POSTs dentro da instância. O lock de Terminal impede duas
Orders ativas criadas por requisições concorrentes. A coluna gerada/unique da V19
impede duas tentativas ativas da mesma Order no MySQL. Retry técnico da mesma tentativa
jamais gera chave nova; retry financeiro terminal cria outro attempt.

## Recuperação

- Webhook: menor latência; não é a única fonte.
- `GET /order/{orderId}/status`: reconcilia tentativa conhecida.
- `GET /pagamento/terminal/{terminalId}/ativo`: descobre tentativa após startup/restart e responde `204` se não houver ativa.
- Startup e scheduler backend: usam `PaymentReconciliationService`.
- Reconnect Terminal: consulta HTTP mesmo sem evento WebSocket.
- `UNKNOWN` sem ID remoto: após `PAYMENT_SUBMISSION_RETRY_COOLDOWN_MS`, repete o mesmo
  POST e a mesma chave idempotente; cada envio renova o cooldown.

No Terminal Python, `active_payment` no SQLite é gravada no primeiro clique. O `cartId` é checkpointado sincronicamente antes do POST financeiro. Startup restaura os IDs, abre “Aguardando pagamento” e executa HTTP em worker. O botão “Verificar novamente” apenas reconcilia. Depois de falhas repetidas, o polling passa de 10 para 30 segundos, mas a tela não retorna às boas-vindas nem habilita nova cobrança.

Callbacks são correlacionados pela tentativa local, `orderId` e `paymentAttemptId`; resposta tardia de tentativa anterior é ignorada. O deadline visual não apaga uma pendência financeira. Heartbeat, telemetria, WebSocket e workers HTTP permanecem separados da thread da UI.

## Cancelamento

`POST /pagamento/terminal/order/{orderId}/cancelamento` exige a credencial interna e
o Terminal proprietário. Persiste `CANCELLING`, intenção e chave idempotente antes do
comando remoto. Timeout conserva o bloqueio; somente `CANCELED`, `EXPIRED` ou outra
falha terminal confirmada libera retry/cupom. `PROCESSED` concorrente prevalece.

## Roteiro manual obrigatório

| Cenário | Resultado esperado |
|---|---|
| Point desligada | UI permanece em “Aguardando pagamento”; nenhum segundo attempt; ligar a Point permite reconciliar ou aguardar expiração. |
| Point continua desligada | Heartbeat/telemetria continuam; polling degradado; retry só após estado terminal. |
| Terminal/Raspberry reinicia | SQLite restaura a pendência; endpoint por Terminal/Order recupera o estado antes de liberar compra. |
| Backend cai após receber | Terminal conserva `PENDING`; quando voltar, backend consulta ou repete idempotentemente a mesma tentativa. |
| Webhook perdido | Scheduler ou status query encontra `APPROVED` e notifica/responde ao Terminal. |
| 100 cliques | Um início local e uma única submissão remota para a tentativa. |
| Cancelamento | `CANCELLING` até confirmação; timeout não libera UI, cupom ou retry. |
| Pagamento aprovado com atraso | `APPROVED` vence; estado não regride e nenhuma segunda cobrança é criada. |

Os cenários físicos exigem credenciais e Point reais. Os testes automatizados simulam persistência/restart, 204/409 estruturados, timeout conservador, callbacks obsoletos, descoberta, cooldown, monotonicidade e concorrência.
