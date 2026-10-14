# Autorização HTTP

Desde 28/09/2026, `SecurityConfig` usa lista explícita e termina em
`anyRequest().denyAll()`. Uma rota nova não se torna pública por omissão.

## Identidades autoritativas

- usuário: JWT → `User` atual no banco → Empresa/Condomínio;
- Terminal: `X-Terminal-Token` → `AuthenticatedTerminal` → Empresa/Condomínio;
- IDs de path, query e body localizam recursos, mas nunca provam ownership.

`/user/{id}` e `/user/{id}/orders` foram mantidos por compatibilidade com o Flutter.
O titular acessa somente o próprio ID. `ADMIN`/`GERENTE` podem consultar somente
usuários da mesma Empresa. Recurso alheio ou cross-tenant é ocultado com 404.

As rotas legadas de carrinho e Order exigem a credencial do Terminal e comparam o
Terminal resolvido pela credencial com o Terminal persistido no recurso. Isso vale
mesmo para dois Terminais da mesma Empresa.

## Checkout

`GET /checkout/carrinho` e `GET /checkout/qrcode` são operacionais do Terminal e
validam ownership. `GET /checkout?id={userId}&session={id}` e
`GET /checkout/session` estão bloqueados: o App Cliente não compra pelo celular e
`body/query userId` não determina mais o proprietário de uma compra.

## Respostas

- 401: credencial ausente ou inválida;
- 403: identidade válida sem papel/ownership operacional;
- 404: recurso inexistente ou inacessível quando ocultar existência evita IDOR;
- 409: conflito de estado;
- 429: limite de recuperação de senha.

Veja também [[idor-protection]], [[password-recovery-security]] e
[[public-endpoints]].
