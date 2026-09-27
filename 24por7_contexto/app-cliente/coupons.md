# Cupons emitidos por recompensa

## Snapshot

O cupom é um snapshot persistido no momento do resgate. `CustomerCoupon` copia:

- nome;
- tipo e valor do desconto;
- valor mínimo da compra;
- desconto máximo;
- emissão (`created_at`) e expiração (`validade`), calculada por `validadeDias`;
- Empresa, usuário e recompensa de origem.

A aplicação do cupom usa exclusivamente esses campos. Alterar uma recompensa de 10%
para 15% não muda o cupom já emitido, que continua em 10%, com mínimo e teto originais.

## Proteção tenant-aware

As FKs compostas garantem no banco:

- `(user_id, empresa_id) → users`;
- `(reward_id, empresa_id) → reward`;
- `(reserved_order_id, empresa_id) → orders`;
- no resgate, User, Reward e Coupon pertencem à mesma Empresa.

`reserved_order_id` é único, impedindo dois cupons simultaneamente associados à mesma
Order. `reward_redemption.coupon_id` também é único, materializando a relação 1:1.
Checks impedem valor não positivo, percentual acima de 100, mínimo negativo e máximo
não positivo.

## Ciclo e limite desta etapa

O ciclo existente `AVAILABLE → RESERVED → USED`, com `EXPIRED/CANCELLED`, foi
preservado. Desativar fidelidade não apaga cupons.

A V18 fecha unicidade e tenant da reserva, mas não resolve o P0 de bloquear a
aplicação depois da criação de um `PaymentAttempt`. A coordenação definitiva entre
fechamento do preço, cupom e tentativa de pagamento permanece no backlog e não foi
mascarada como concluída.
