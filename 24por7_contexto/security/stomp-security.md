# Segurança STOMP

O handshake HTTP de `/ws` usa o mesmo interceptor, header e Principal dos sockets
nativos. Headers STOMP `CONNECT` não criam uma segunda identidade: `CONNECT` apenas
confirma que o handshake já possui `AuthenticatedTerminal` válido.

O broker atual é somente server-push:

- `SUBSCRIBE` permitido somente em `/topic/payment/{terminalId próprio}`;
- `SUBSCRIBE` para outro Terminal, inclusive da mesma Empresa, é negado;
- não existem handlers `@MessageMapping`; todo `SEND /app/**` é negado;
- `MESSAGE` outbound revalida a sessão, bloqueando entrega após revogação, rotação ou
  desativação;
- `DISCONNECT` remove a identidade do registro de sessões.

O cliente Python deve enviar `X-Terminal-Token` no upgrade de `/ws`, então realizar
`CONNECT` normal e subscrever apenas seu tópico. `401`/falha de handshake exige
reprovisionamento; erro STOMP ou disconnect encerra a sessão e não autoriza trocar o
UUID da destination.
