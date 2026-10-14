# E2E Terminal Python ↔ Backend

Executado em 28 de setembro de 2026 com processos reais: Terminal Python,
Spring Boot, MySQL 8.4 e Redis 8. Somente Mercado Pago Point e a entrega final
do comprovante foram stubados. Nenhuma chamada financeira real é permitida.

## Comando

Pré-requisitos: Docker/Compose, Java 17, Maven wrapper, `openssl`, `curl` e o
virtualenv do Terminal em `../TerminalPython/.venv`.

```bash
cd /home/jefiro/Documentos/projetos/app247
./scripts/e2e-terminal-backend.sh
```

O script gera secrets efêmeros em variáveis de ambiente, sobe o Compose
`app247-terminal-e2e`, inicia o Spring com profile `prod` e banco
`app247_e2e`, aguarda o health check, deixa o Flyway aplicar as 20 migrations,
insere fixtures somente depois do schema, executa os processos Python, reinicia
o backend durante uma tentativa PENDING e remove processos, containers, volumes
e arquivos temporários ao sair.

O caminho do Terminal pode ser substituído com
`TERMINAL_PYTHON_ROOT=/caminho/TerminalPython`.

## Validação das suítes

- Backend: `./mvnw clean test` — 386 testes, 0 falhas, 0 erros, 0 ignorados.
- Terminal: `python -m compileall .` — OK.
- Terminal: `python -m pytest` — 198 testes aprovados, 0 falhas, 0 ignorados.
- SQLite do E2E: `PRAGMA integrity_check` — `ok`.

## Isolamento

- MySQL: `mysql:8.4`, porta local `33306`, dados em `tmpfs`.
- Redis: `redis:8-alpine`, porta local `36379`, dados em `tmpfs`.
- Spring: porta `38080`, Point apontado por `MP_API_BASE_URL` ao stub.
- Point stub: porta `39091`, identidade remota, idempotência, contador, timeout e estados controláveis.
- Comprovante stub: porta `39092`; prova Terminal → Spring sem FastAPI/n8n.
- Terminal: credential e SQLite em diretório `mktemp`, fora do Git.

## Matriz executada

| Fluxo | Processos reais | Resultado |
|---|---:|---|
| Device credential/migração legado | Sim | OK; arquivo `0600` |
| HTTP auth válida/ausente/inválida | Sim | OK; 200/401 `AUTH_REQUIRED`/401 `TERMINAL_AUTH_INVALID` |
| 403 Terminal desativado | Sim | OK; credential e cursor preservados |
| FULL sync | Sim | OK; MySQL → JPA → HTTP → parser → SQLite |
| FULL vazio | Sim | OK; item local obsoleto desativado |
| Incremental/cursor UTC | Sim | OK |
| Cursor em erro | Sim | OK; não avançou |
| PRODUCT_SYNC_REQUIRED/coalescing | Não | NÃO VALIDADO E2E |
| Heartbeat/ACK | Sim | OK; `last_ping` persistido |
| Heartbeat Terminal errado | Não | NÃO VALIDADO E2E |
| Telemetria | Sim | OK; HTTP 202 |
| Lifecycle startup/online/shutdown | Não | NÃO VALIDADO E2E |
| Payment socket/evento server-push | Não | NÃO VALIDADO E2E |
| Carrinho/Order/PaymentAttempt | Sim | OK; persistidos no MySQL |
| PENDING | Sim | OK; sem segunda cobrança |
| PROCESSING | Não | NÃO VALIDADO E2E |
| ACTION_REQUIRED | Não | NÃO VALIDADO E2E |
| Timeout → UNKNOWN | Sim | OK |
| UNKNOWN → APPROVED | Sim | OK por reconciliação |
| APPROVED | Sim | OK; Order e attempt `PROCESSED` |
| Aprovação repetida | Não | NÃO VALIDADO E2E |
| REJECTED + retry | Sim | OK; mesma Order, attempts #1 e #2 |
| Duplicidade no timeout/retry | Sim | OK; contador Point = 1 |
| Cancelamento | Sim | OK; `CANCELED` |
| Cancelamento timeout | Não | NÃO VALIDADO E2E |
| Restart Terminal/SQLite recovery | Sim | OK; processos Python distintos |
| Restart Backend durante PENDING | Sim | OK; mesma tentativa recuperada |
| Redis restart | Não | NÃO VALIDADO E2E |
| Comprovante Terminal → Spring | Sim | OK; entrega externa stubada |
| Factory reset | Não | NÃO VALIDADO E2E |
| Credential revogada/reprovisionamento | Não | NÃO VALIDADO E2E |

## Divergências encontradas

| Divergência | Lado incorreto | Correção |
|---|---|---|
| Header ausente e inválido produziam o mesmo código | Backend | `AUTH_REQUIRED` para ausente/vazio; `TERMINAL_AUTH_INVALID` para valor inválido |
| Spring Security negava o upgrade antes do interceptor | Backend | sockets passam pelo filtro de device e exigem `ROLE_TERMINAL`; sem `permitAll` |
| URL Point era fixa em produção | Backend/adaptador | `MP_API_BASE_URL`, mantendo a URL oficial como default |
| Stub não lia request HTTP chunked | Harness E2E | parser chunked no stub |
| Stub não avançava versão remota | Harness E2E | versão monotônica por transição/cancelamento |

## Limites

Este ensaio não substitui a validação física posterior com Raspberry, scanner e
Point. Os itens marcados `NÃO VALIDADO E2E` não são inferidos das suítes isoladas.
