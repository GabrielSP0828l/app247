# Segurança dos WebSockets nativos

`/terminal-socket` e `/payment-socket/{terminalId}` executam
`TerminalHandshakeInterceptor` antes do upgrade. A credencial vem apenas de
`X-Terminal-Token`; o interceptor grava na sessão somente `terminalId`, `empresaId`,
`condominioId` e o `AuthenticatedTerminal`, nunca o segredo.

No upgrade HTTP, o filtro de device cria o Principal e o Spring Security exige
`ROLE_TERMINAL` nessas rotas. O interceptor aplica em seguida o vínculo exato de
identidade/tenant; as rotas não usam `permitAll` nem aceitam o token global legado.

Em `/payment-socket/{id}`, a identidade resolvida deve ser exatamente o path. Em
`/terminal-socket`, a credencial sozinha determina a identidade. O heartbeat mantém
`terminalId` no JSON por compatibilidade, mas divergência fecha a conexão com policy
violation antes de qualquer update. Cada heartbeat revalida versão, revogação e estado
operacional.

O payment socket usa o Principal, não o URI, para registrar a sessão. Antes de cada
evento financeiro revalida o estado operacional; evento de factory reset usa a
validação lifecycle para alcançar um Terminal em `RESET_REQUIRED`.

Origins são a lista CSV `app.websocket.allowed-origins` /
`WEBSOCKET_ALLOWED_ORIGINS`. Desenvolvimento usa localhost explícito. O profile prod
tem default vazio (same-origin/sem curinga) e o Compose exige valor. Clientes Python
sem header `Origin` não são transformados em browser; em todos os casos a credencial
continua obrigatória.
