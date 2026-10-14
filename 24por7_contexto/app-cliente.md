# App Cliente — contratos autenticados

O backend expõe uma fachada móvel sob `/app-cliente`. Todas as rotas exigem JWT
com `ROLE_USER`; não aceitam `userId`, `empresaId`, `condominioId` ou `terminalId`.
O principal autenticado determina o usuário, a Empresa e o Condomínio, e usuários
sem vínculo recebem `TENANT_LINK_REQUIRED`; Condomínio ausente ou inativo produz
`CONDOMINIUM_REQUIRED`.

O cadastro cria uma conta global ainda sem tenant. O vínculo é consumido por
`POST /app-cliente/tenant-link` usando convite opaco emitido por gestor, sem aceitar
IDs de tenant do cliente. Veja [[app-cliente/account-tenant-link]].

## Rotas

| Método | Rota | Comportamento |
|---|---|---|
| GET | `/app-cliente/bootstrap` | Usuário, Condomínio, flags, modo de fidelidade e hora UTC. |
| GET | `/app-cliente/catalogo` | Catálogo paginado do estoque do Condomínio com promoção e preço calculados no backend. |
| GET | `/app-cliente/compras` | Histórico paginado do usuário do JWT. |
| GET | `/app-cliente/compras/{id}` | Detalhe com `OrderItem` histórico; consulta combina Order, usuário e Empresa. |
| GET | `/app-cliente/compras/{id}/comprovante` | PNG gerado para visualização, sem efeitos de envio. |
| POST | `/app-cliente/compras/{id}/comprovante/envios` | Envio explícito por `EMAIL` ou `WHATSAPP`. |
| POST | `/app-cliente/identificacao-tokens` | `qrPayload` opaco, aleatório, descartável e válido por dois minutos. |
| GET | `/app-cliente/fidelidade` | Modo, saldo e movimentos da conta. |
| GET/POST | `/app-cliente/recompensas` e `/{id}/resgates` | Elegibilidade e resgate transacional/idempotente. |
| GET/POST | `/app-cliente/cupons` e `/{id}/tokens` | Cupons do usuário e token de aplicação descartável. |
| GET/PUT | `/app-cliente/preferencias` | Preferências de envio automático. |
| POST | `/app-cliente/compras/{id}/suporte` | Abre protocolo somente para Order própria. |

As 15 rotas acima são os contratos originais. O bloco P1-A acrescenta a rota de
vínculo `POST /app-cliente/tenant-link` e a emissão administrativa
`POST /admin/app-cliente/tenant-invitations` sem substituir os controllers existentes.

Features operacionais desativadas retornam `409` com erro estruturado e não consultam
recursos de outro tenant. Sem `app_cliente_config`, a Empresa usa defaults seguros
com todas as features desativadas e modo `NONE`. A configuração 1:1 por Empresa é
gerida em `/admin/app-cliente/**`; veja [[app-cliente/app-client-config]] e
[[app-cliente/loyalty-config]].

## Tokens e Terminal

Os tokens de identificação são 256 bits gerados por `SecureRandom`, codificados em
Base64 URL-safe e entregues no envelope `app247://customer-link/`. Somente SHA-256 é
persistido no MySQL, com TTL padrão de dois minutos, invalidação da emissão anterior,
consumo atômico e referência ao carrinho consumidor. CPF, JWT e IDs não aparecem no
QR. Detalhes em [[app-cliente/customer-terminal-link]]. Tokens de cupom continuam no
contrato próprio em Redis.

O Terminal chama `POST /terminal/customer-link/consume` e
`POST /terminal/coupons/apply`, autenticando-se com a credencial individual em
`X-Terminal-Token`. O primeiro vincula a conta ao Carrinho aberto no
mesmo Terminal/Condomínio/Empresa; a Order herda o usuário sem recebê-lo no request.
O segundo calcula o desconto no backend sobre o
total pós-promoções, valida mínimo/máximo e reserva o cupom. Aprovação muda a reserva
para `USED`; falha ou cancelamento a libera quando ainda válida.

## Fidelidade e atomicidade

Somente o evento de uma Order `PROCESSED` com `order.user` preenchido credita pontos
ou cashback. Orders sem conta vinculada, inclusive histórico identificado apenas por
CPF, não geram benefício. O movimento `purchase:{orderId}` torna o crédito idempotente.

O resgate exige `Idempotency-Key`. Na mesma transação relacional, a conta é bloqueada,
o saldo é validado/debitado, o cupom é criado e o resgate/movimento são persistidos.
A constraint `(user_id, idempotency_key)` protege retries concorrentes.

Recompensas são expostas somente em `POINTS`, filtradas pela Empresa e pelo escopo
legado opcional de Condomínio. Elegibilidade e limite por usuário são calculados pelo
backend. O cupom copia nome, tipo, valor, mínimo, máximo e expiração; editar a
recompensa depois não altera a emissão. Veja [[app-cliente/rewards]] e
[[app-cliente/coupons]].

## Persistência

A migration `V15__app_cliente.sql` cria configuração, conta/movimentos de fidelidade,
recompensas, cupons, resgates, preferências e suporte. As relações operacionais
repetem ou validam a Empresa por FK tenant-aware. A V16 acrescenta cadastro global,
estado de verificação e `CustomerIdentity`; a V17 cria os convites de vínculo e suas
constraints. A V18 migra a configuração para Empresa, adiciona os campos de gestão de
recompensa e fortalece checks/FKs financeiras e tenant-aware.
