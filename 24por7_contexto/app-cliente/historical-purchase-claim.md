# Claim de compras históricas

Uma compra feita sem conta pode guardar `CustomerIdentity`, criada a partir de
`HMAC-SHA256(CPF normalizado, IDENTITY_HASH_SECRET)` e vinculada à Empresa. Esse
registro não representa, sozinho, ownership autenticado.

## Quando o claim roda

O claim é chamado dentro da mesma transação que consome o convite de vínculo, depois
de o backend:

- autenticar a conta;
- provar que o convite pertence ao CPF da conta;
- fixar Empresa e Condomínio autorizados;
- preencher `cpf_verified_at`.

## Algoritmo e isolamento

`HistoricalPurchaseOwnershipService.claimVerified` rejeita CPF não verificado ou
conta sem Empresa. Em seguida calcula o HMAC do CPF e executa uma busca com
`PESSIMISTIC_WRITE` por **Empresa + cpf_hash**. A identidade encontrada recebe
`claimed_user_id` e `claimed_at`.

A FK composta
`customer_identity(claimed_user_id, empresa_id) → users(id, empresa_id)` reforça o
tenant no banco. Identidade com o mesmo CPF em outra Empresa não é consultada nem
alterada. Retry do mesmo usuário é estável; identidade já claimed por outro usuário
gera conflito.

O histórico exige simultaneamente:

- `orders.empresa_id` igual à Empresa autenticada;
- `orders.condominio_id` igual ao Condomínio atual da conta;
- `orders.user_id` igual ao usuário **ou** a `CustomerIdentity` claimed pelo usuário.

Detalhes usam a mesma consulta de ownership e retornam `PURCHASE_NOT_FOUND` para IDs
de outro usuário, Condomínio ou Empresa.

## Fidelidade retroativa

Claim altera somente `CustomerIdentity`; não altera a Order, não publica
`OrderPaidEvent` e não chama o serviço de fidelidade. Além disso,
`LoyaltyAccrualService` exige `order.user != null`. Portanto:

`CPF_HISTORY claimed → aparece no histórico → 0 pontos novos → R$ 0 cashback novo`.

O teste de integração confirma que nenhuma `LoyaltyAccount` ou `LoyaltyMovement` é
criada pelo claim.
