# Proteção contra IDOR

## Usuário

O backend recarrega o principal autenticado e aplica `requireSelf` ou
`requireSelfOrTenantManager`. Alteração de senha e foto são estritamente próprias.
Campos administrativos (`ativo` e `condominioId`) só podem ser alterados por
`ADMIN`/`GERENTE` da mesma Empresa. Respostas usam DTO e não serializam senha,
tokens ou segredos internos.

## Order, carrinho e comprovante

Carrinho e Order são carregados antes da operação e comparados com
`AuthenticatedTerminal`. A comparação inclui o ID exato do equipamento, não apenas
a Empresa. O comprovante requer a mesma identidade, Order aprovada e associação
Order/Terminal; o comprovante do App Cliente permanece na fachada autenticada e
consulta somente compra própria.

## Cenários cobertos

- User A → User/Order A: permitido;
- User A → User/Order B, inclusive mesma Empresa: ocultado/rejeitado;
- gestor Empresa A → usuário Empresa B: ocultado;
- Terminal A1 → carrinho/Order A2 na mesma Empresa: 403;
- Terminal A → recurso da Empresa B: 403;
- checkout móvel tentando escolher `userId`: rota bloqueada;
- chamada anônima a carrinho, Order, checkout operacional ou comprovante: 401.

As queries de histórico do usuário incluem `user_id` e `empresa_id`; a proteção não
depende de um `findById` isolado seguido de exposição do objeto JPA.
