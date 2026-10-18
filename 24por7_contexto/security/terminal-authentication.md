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

## Configuração legada (removida)

Desde 18/10/2026 não existe mais `TERMINAL_INTERNAL_TOKEN`, `TERMINAL_LEGACY_AUTH_ENABLED`
nem `POST /terminal/{terminalId}/credential/migrate` (a rota cai no `denyAll` → 401).
A única forma de obter credencial é o gestor emitir em `POST /terminais/{id}/credential`
e o operador instalar no equipamento (`--install-device-credential`, via stdin).
Antes do deploy, confirmar que nenhum Terminal ativo está sem credencial:
`SELECT id FROM terminal WHERE ativo = 1 AND device_credential_hash IS NULL;`.

## Detecção de uso anômalo

`TerminalCredentialAnomalyService` é chamado pelo `TerminalHttpAuthenticationFilter`
(HTTP e handshakes WebSocket) após autenticação válida. Guarda no Redis a última
origem (`request.getRemoteAddr()`) por Terminal por `app.terminal.anomaly.origin-window`
(padrão `PT10M`). Se a mesma credencial aparecer de outra origem dentro da janela,
registra `WARN [TERMINAL-ANOMALY]` e `audit_log` com ação
`TERMINAL_CREDENTIAL_ORIGIN_CHANGED` (metadata: origens, `credentialVersion`), no
máximo uma vez por `app.terminal.anomaly.alert-cooldown` (padrão `PT1H`). Nunca
bloqueia a requisição; falha de Redis/auditoria é ignorada. Desligável com
`app.terminal.anomaly.enabled=false`.

Limitações: troca legítima de IP do provedor gera um alerta; enquanto a entrada for
ngrok/porta exposta, `X-Forwarded-For` é aceito do cliente (`forward-headers-strategy=framework`)
e a origem pode ser forjada. Com nginx sobrescrevendo `X-Forwarded-For`, a origem fica
confiável. Não há alerta por horário porque não existe configuração de horário de
funcionamento por Condomínio. Resposta a um alerta: rotacionar ou revogar a credencial
em `/terminais/{id}/credential`.

## Contrato implementado no Terminal Python

1. Recebe a credencial instalada pelo operador (a chamada a `credential/migrate`
   que o cliente ainda contém agora recebe 401 e cai no estado de aguardar credencial).
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
