# Autenticação operacional do Terminal

Voltar para [[../00-index]]. Credenciais em [[terminal-device-credentials]], sockets em
[[websocket-security]] e STOMP em [[stomp-security]].

## Identidade autoritativa

Operações diárias enviam `X-Terminal-Token: <device credential>`. A credencial resolve
exatamente um registro e produz `AuthenticatedTerminal(terminalId, empresaId,
condominioId, credentialVersion)`. IDs em path, header ou body são apenas restrições:
se não coincidirem com a identidade resolvida, a resposta é `403
TERMINAL_IDENTITY_MISMATCH`.

Credencial ausente/vazia retorna `401 AUTH_REQUIRED`. Credencial presente, mas
inválida, rotacionada ou revogada, retorna `401 TERMINAL_AUTH_INVALID`. Credencial
válida de Terminal/Condomínio/Empresa inativo ou
encerrado retorna `403 TERMINAL_NOT_OPERATIONAL`. O segredo nunca é aceito em query
string e não é registrado em logs.

## HTTP operacional

Exigem a credencial individual:

- `GET /produtos/sync?uuidTerminal=...` e `GET /produtos/home`;
- `GET /terminais/{terminalId}/produtos-disponiveis`;
- `POST /terminal/telemetry`;
- `GET /terminal/{terminalId}/bootstrap`;
- `POST /terminal/{terminalId}/factory-reset/started|completed`;
- criação de carrinho, status/início/recuperação/cancelamento Point já protegidos;
- consumo de identificação/cupom do Terminal e o comprovante operacional existente.

`GET /terminal/serial/{serial}` permanece público como exceção de descoberta inicial.
Ele não concede identidade operacional nem retorna credencial.

O bootstrap pode consultar o estado com credencial válida mesmo quando o equipamento
está desativado. As confirmações de factory reset exigem explicitamente
`RESET_REQUIRED`; as demais operações exigem Terminal plenamente operacional.
Ao concluir factory reset, a credencial é revogada.

## Configuração legada

`TERMINAL_INTERNAL_TOKEN` é segredo compartilhado somente de migração. Ele é aceito
exclusivamente em `POST /terminal/{terminalId}/credential/migrate`, e apenas quando
`TERMINAL_LEGACY_AUTH_ENABLED=true`. Se o modo estiver habilitado sem segredo, o
startup falha. O token global nunca autentica HTTP diário, WebSocket ou STOMP.

## Contrato implementado no Terminal Python

1. Durante migração controlada, chama uma única vez a rota `credential/migrate` com
   o token legado em `X-Terminal-Token`.
2. Persiste o campo `credential` retornado em arquivo local restrito (`0600` no
   Linux); ele não poderá ser consultado novamente.
3. Envia essa credencial no header `X-Terminal-Token` em todo HTTP e handshake
   WebSocket/STOMP.
4. Em `401`, interrompe a operação sem apagar recovery financeiro e exige reprovisionamento
   autorizado. Em `403`, não trocar IDs: interromper e exibir estado desabilitado ou
   erro de vínculo. Em disconnect, reconectar com a mesma credencial somente enquanto
   ela continuar válida.

O cliente não usa STOMP atualmente; usa os dois sockets nativos. Em Python, o
handshake suprime `Origin` e continua autenticado pela credential. A migração em
produção permanece desabilitada até o E2E conjunto e o rollout operacional.
