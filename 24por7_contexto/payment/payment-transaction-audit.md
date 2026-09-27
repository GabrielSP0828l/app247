# Auditoria — transação de preço, cupom e pagamento Point

Auditoria iniciada em 27/09/2026 sobre o código executável e as migrations V1–V18.
As conclusões abaixo descrevem o estado anterior; a implementação resultante está na
V19 e nos documentos de pagamento vinculados no índice.

## Estado encontrado antes deste bloco

| Área | Estado | Evidência e risco |
|---|---|---|
| Preço autoritativo | **PARCIAL** | `CarrinhoService` recalcula Produto e promoção com `PricingService`; o Terminal envia apenas expectativa. Porém `OrderService.criarCobranca` reprecifica inclusive uma Order já existente, permitindo mudar o total em retry. |
| Snapshot de item | **EXISTENTE** | `OrderItem` copia SKU, nome, unidade, quantidade, preço original/aplicado, promoção, desconto e subtotal. |
| Snapshot da Order | **PARCIAL** | `subtotal`, `desconto`, `total_calculado` e `total_cobrado` existem, mas promoção e cupom não possuem totais separados e não existe marco persistido de fechamento. |
| Pipeline financeira | **PARCIAL** | Promoção é calculada em `CarrinhoService`; cupom recalcula diretamente em `TerminalQrService`, duplicando política e sobrescrevendo o total da Order. |
| Cupom | **PARCIAL** | Usa lock pessimista, `AVAILABLE → RESERVED` e `reserved_order_id` único. Aceita cupom em Order `CREATED`, isto é, potencialmente depois do POST remoto. |
| Ciclo do cupom | **PARCIAL** | `OrderPaidEvent` usa o cupom e eventos terminais o liberam. Não há validação obrigatória da reserva antes da cobrança nem vínculo-snapshot explícito na Order. |
| PaymentAttempt local antes do POST | **EXISTENTE** | `PointPaymentPersistenceService.prepare` usa `REQUIRES_NEW`; o POST ocorre depois do commit e a resposta é persistida em outra transação. |
| Cardinalidade 0..N | **PARCIAL** | Tabela e entidade suportam N e têm `UNIQUE(order_id, attempt_number)`, mas o service sempre reutiliza a tentativa mais recente e nunca cria retry financeiro. |
| Tentativa ativa única | **PARCIAL** | Locks de Carrinho/Terminal reduzem corrida, mas o banco não impede duas linhas ativas para a mesma Order. |
| Timeout | **PARCIAL** | Mantém tentativa `PENDING` sem ID remoto e repete a mesma chave; não possui estado explícito `UNKNOWN`. |
| Monotonicidade | **EXISTENTE/PARCIAL** | Versionamento remoto, lock e `OrderStatus.canTransitionTo` impedem regressões. A transição ainda assume que a tentativa recebida é sempre a tentativa atual da Order. |
| Evento de aprovação | **EXISTENTE** | `OrderPaidEvent` só nasce quando o estado da Order muda para `PROCESSED`; `payment_event` e efeitos de estoque possuem idempotência. |
| Autorização do Terminal | **AUSENTE no pagamento** | Rotas `/pagamento/**`, status e criação de carrinho estão públicas; apenas os endpoints de QR usam `X-Terminal-Token`. |
| Tenant | **PARCIAL** | O domínio deriva Empresa/Condomínio do Terminal e possui FKs compostas, mas o chamador do pagamento não prova ser um Terminal autorizado. |

## Fluxo anterior exato

1. `POST /carrinho` validava produtos e calculava promoções.
2. `OrderService.createOrder` reprecificava, congelava itens e reservava estoque.
3. `POST /terminal/coupons/apply` podia alterar diretamente os totais da Order enquanto
   ela estivesse `PENDING` **ou `CREATED`**.
4. `PointPaymentPersistenceService.prepare` voltava a chamar `criarCobranca`; em Order
   existente, o carrinho era reprecificado e os totais eram sobrescritos.
5. A tentativa local era persistida antes do POST remoto, mas somente a primeira era
   criada; retry reutilizava a mesma linha.
6. Timeout deixava `PENDING`; webhook/reconciliação aplicavam o estado posteriormente.

## Direção deste bloco

- separar desconto promocional e desconto de cupom no snapshot da Order;
- centralizar a composição final e persistir `price_finalized_at`;
- proibir cupom/reprecificação depois do fechamento;
- criar uma nova tentativa para retry financeiro, preservando reenvio técnico da mesma
  tentativa e sua chave idempotente;
- representar resultado remoto incerto como `UNKNOWN` e cancelamento em curso como
  `CANCELLING`;
- usar lock da Order e uma chave gerada/única no MySQL para impedir duas tentativas
  ativas;
- validar autenticação, identidade e estado operacional do Terminal antes de qualquer
  criação/submissão financeira.

## Resultado implementado

| Área | Resultado P1-C |
|---|---|
| Preço | pipeline central, snapshot agregado e por item, `price_finalized_at` e `CHECK` financeiro |
| Cupom | uma reserva por cupom, um cupom por Order, ownership/tenant, consumo somente em aprovação e liberação somente terminal |
| Attempts | retry cria novo número; `UNKNOWN`/`CANCELLING`; valor e idempotência por attempt |
| Concorrência | locks curtos + coluna gerada/unique impedem duas tentativas ativas por Order |
| Provider | attempt confirmado antes do POST; IDs/status persistidos depois; amount vem do attempt congelado |
| Transições | aprovação não regressiva, evento pago único e callback duplicado idempotente |
| Terminal | token interno, Terminal operacional e ownership Empresa/Condomínio/Carrinho/Order |
| Testes | preço/cupom/retry/timeout/transições unitários e HTTP concorrente/cross-tenant com MySQL/Flyway |

Gap de rollout: o `PurchaseApi.py` do Terminal de referência ainda não envia os novos
headers. O projeto do Terminal não foi alterado por estar fora do escopo autorizado.
