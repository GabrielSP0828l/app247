# Integração Terminal Python ↔ Backend

Atualizado em 28 de setembro de 2026. Este documento espelha o contrato
implementado no cliente em `TerminalPython/docs/backend-contracts.md`; o OpenAPI
continua sendo a fonte normativa das rotas HTTP.

## Identidade e ativação

`GET /terminal/serial/{serial}` é apenas descoberta pública. Operação diária usa
`X-Terminal-Token` com a credential individual `tdc_...`, que resolve um único
Terminal, Empresa e Condomínio. `terminalId` em path/header/body é validado, não
é prova de identidade.

O Terminal Python persiste o segredo fora do JSON/SQLite/Git em arquivo local
`0600`, com instalação/rotação atômica. A migração por `TERMINAL_INTERNAL_TOKEN`
foi removida do backend em 18/10/2026; o código de migração ainda presente no
Terminal Python passa a receber 401. Não existe fallback após 401.
`GET /terminal/serial/{serial}` devolve só `terminalId`, `serialNumber`, `ativo` e
`activated`; nome, código e Condomínio gravados em `terminal.json` ficam `null`.

## Matriz operacional

| Área | Backend | Terminal | Verificação atual |
|---|---|---|---|
| Auth device | hash V20 + `AuthenticatedTerminal` | arquivo 0600 + cliente central | suítes dos dois projetos |
| Activation | descoberta sem segredo + migração/admin | persiste antes de operar | teste de provisionamento |
| Sync full | `/produtos/sync` tenant-aware | transação SQLite | testes de contrato |
| Sync incremental | cursor UTC + tombstones | coalescing `sync_pending` | testes de contrato |
| Heartbeat | Principal autoritativo + ACK | id próprio + valida ACK | testes socket/backend |
| Telemetry | DTO e Principal | payload real do Raspberry | testes de contrato |
| WebSocket | handshake individual | `Origin` suprimido, header obrigatório | testes isolados dos lados |
| STOMP | `/ws`, SUBSCRIBE próprio, SEND negado | não utilizado | backend testado; N/A cliente |
| Order | cart/tenant/preço backend | ids + quantidade, sem total autoritativo | testes de contrato |
| PaymentAttempt | tentativa ativa/UNKNOWN | persiste attemptId e bloqueia retry | testes financeiros |
| Cancel | idempotente e reconciliado | não presume cancelamento em timeout | testes financeiros |
| Recovery | ativo/status persistente | checkpoint SQLite | testes de restart isolados |
| Receipt | ownership + APPROVED | somente via Spring | testes de contrato |

## WebSocket

O cliente não-browser não envia `Origin`; isso evita misturar allowlist de
navegador com autenticação de dispositivo. `/terminal-socket` e
`/payment-socket/{terminalId}` continuam exigindo credential no handshake. 401
encerra o reconnect automático e coloca o kiosk em modo administrativo seguro;
403 preserva a credential, mas bloqueia novas operações.

`PRODUCT_SYNC_REQUIRED` agenda sync sem permitir workers simultâneos. Heartbeat
mantém `terminalId` por compatibilidade e o backend exige igualdade com o
Principal.

## Pagamento e recovery

O Terminal envia o carrinho; o backend precifica e congela Order/PaymentAttempt.
O cliente persiste `cartId`, `orderId`, `paymentAttemptId` e status. Timeout HTTP,
`PENDING`, `PROCESSING`, `ACTION_REQUIRED`, `UNKNOWN` ou `CANCELLING` nunca
autorizam uma segunda cobrança. `ACTIVE_PAYMENT_ATTEMPT_EXISTS` e
`PAYMENT_ALREADY_ACTIVE` levam à recuperação da tentativa existente.

Resposta JSON com campos adicionais é tolerada. Resultado final só é aplicado
quando Order/Terminal/attempt são correlacionados.

## Reset

Reset de catálogo preserva a credential. Factory reset local remove-a. Factory
reset remoto preserva-a apenas para autenticar `/factory-reset/completed`; após o
ACK ela é apagada. PaymentAttempt não terminal bloqueia qualquer reset.

## Validação antes do rollout

O ensaio automatizado com processo Spring real, MySQL 8.4, Redis 8, Terminal
Python e provider Point stubado foi executado em 28 de setembro de 2026. A
matriz, o comando reproduzível e os cenários ainda não validados estão em
[[e2e-terminal-backend]]. Continua pendente o checklist físico no Raspberry,
scanner e Point real; a autenticação legado permanece proibida em produção.
