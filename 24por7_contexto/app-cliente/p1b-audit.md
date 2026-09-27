# Auditoria P1-B — configuração, fidelidade e recompensas

Auditoria iniciada em 26/09/2026 e concluída em 27/09/2026. Fonte primária:
entidades, services, repositories, testes e migrations executáveis V1–V18. O bloco
P1-C posterior acrescentou a V19 sem alterar as migrations auditadas aqui.

| Área | Estado anterior | Decisão P1-B |
|---|---|---|
| `AppClienteConfig` | **PARCIAL**: já existe, mas a PK é `condominio_id`, os defaults são todos habilitados e flags de fidelidade são independentes entre si. | **REUTILIZADO/ALTERADO**: passa a existir uma linha por Empresa, com default seguro desabilitado e invariantes coerentes. |
| Configuração App Cliente por Empresa | **AUSENTE** | **ALTERADO** na tabela existente; não será criada configuração global ou paralela. |
| `FidelidadeConfig` separada | **AUSENTE** | **NÃO CRIADA**: modo e taxas existentes em `app_cliente_config` serão reutilizados, expostos por API administrativa separada. |
| Modos `NONE/POINTS/CASHBACK` | **EXISTENTE** | **REUTILIZADO/ALTERADO** com validação de service e CHECKs de banco. `POINTS_AND_CASHBACK` permanece inexistente. |
| `LoyaltyAccount` | **EXISTENTE** com FK composta `User/Empresa` e unicidade por usuário. | **REUTILIZADO**; desligar fidelidade não apaga saldo ou movimentos. |
| `LoyaltyMovement` | **PARCIAL**: Order usa FK simples. | **ALTERADO** para FK tenant-aware por `order_id + empresa_id`, sem ampliar o ledger. |
| `Reward` | **PARCIAL**: Empresa, escopo opcional de Condomínio, valores, custo, validade e ativo já existem; falta CRUD, descrição, validade em dias, limite por usuário e checks financeiros. | **REUTILIZADO/ALTERADO**; recompensa P1-B passa a ser por Empresa e CRUD é tenant-aware. |
| `CustomerCoupon` | **PARCIAL**: já copia nome, tipo, valor, mínimo, máximo e validade, portanto o snapshot financeiro existe. User e Reward já usam FKs tenant-aware; Order reservada usa FK simples. | **REUTILIZADO/ALTERADO**: snapshot preservado e FK da Order tornada tenant-aware. |
| `RewardRedemption` | **PARCIAL**: User/Reward/Coupon já são tenant-aware; falta `UNIQUE(coupon_id)` correspondente ao `@OneToOne` e checks. | **ALTERADO** com unicidade e constraints financeiras. |
| APIs administrativas | **AUSENTE** | **CRIADO** GET/PUT de configuração, GET/PUT de fidelidade e CRUD soft-delete de recompensas sob `/admin/app-cliente/**`. |
| Bootstrap/cliente | **PARCIAL**: lê configuração por Condomínio e usa defaults permissivos. | **ALTERADO** para Empresa e flags derivadas coerentes. |
| OpenAPI | **AUSENTE** para o P1-B | **CRIADO/ALTERADO** nos novos controllers e contratos. |

## Constatações importantes

- A Empresa é obtida somente por `EmpresaContext`; nenhuma API administrativa P1-B
  aceita `empresaId`.
- O schema atual já usava `DECIMAL(15,6)` para dinheiro e `DECIMAL(9,6)` para
  percentual; essa precisão foi preservada.
- O cupom não reconstrói suas regras pela recompensa: os campos financeiros são
  copiados no resgate. A alteração da recompensa não muda cupons emitidos.
- Os códigos legados de desconto eram `PERCENTAGE/FIXED`. O contrato administrativo
  P1-B adotou `PERCENTUAL/VALOR_FIXO`, migrando os dados existentes de forma
  incremental e ajustando o consumidor do cupom.
- A V18 foi criada incrementalmente; nenhuma migration V1–V17 foi editada.

## Resultado implementado

- configuração explícita 1:1 por Empresa e fallback seguro desabilitado;
- APIs administrativas de configuração, fidelidade e recompensa sem IDs de tenant;
- bootstrap e accrual lendo a mesma configuração por Empresa;
- `NONE`, `POINTS` e `CASHBACK` com invariantes Java + MySQL;
- CRUD paginado de recompensas, soft delete e elegibilidade calculada no backend;
- resgate tenant-aware com limite por usuário e snapshot financeiro de cupom;
- FKs compostas e checks financeiros em config, Reward, Coupon, LoyaltyAccount,
  LoyaltyMovement e RewardRedemption;
- esquema Bearer e contratos administrativos no OpenAPI;
- integração HTTP real em MySQL/Flyway, sem mock do service principal.

O P0 de impedir aplicação de cupom depois da criação de `PaymentAttempt` permanece
fora do P1-B. Ledgers completos, cashback como pagamento e estornos também não foram
implementados.
