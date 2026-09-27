# Concorrência e idempotência do pagamento

## Barreiras locais

O início do pagamento usa transações curtas e locks pessimistas sobre Carrinho,
Empresa e Terminal. `orders` também possui `@Version`. Dentro de uma instância,
`PointPaymentSubmissionService` serializa submissões da mesma Order; a garantia entre
instâncias vem da persistência e da chave idempotente do provider.

No MySQL, a V19 cria `payment_attempt.active_order_id` como coluna gerada: ela recebe
`order_id` somente quando o status está em `PENDING`, `ACTION_REQUIRED`, `UNKNOWN` ou
`CANCELLING`. `UNIQUE(active_order_id)` impede duas tentativas ativas para a mesma
Order sem depender de partial index, que o MySQL não oferece nesse formato.

As constraints existentes também garantem unicidade de:

- `(order_id, attempt_number)`;
- `(provider, idempotency_key)`;
- `(provider, external_reference)`;
- IDs remotos não nulos do provider.

## Dois requests simultâneos

O primeiro request persiste o attempt e se torna o único dono da submissão remota. O
segundo, depois do lock, encontra o mesmo attempt e retorna/retoma seu estado sem fazer
novo POST. O teste HTTP real com duas threads, controller, autorização, services,
repositories e MySQL confirma uma linha ativa e uma chamada ao mock externo.

Para outro carrinho do mesmo Terminal, uma Order não resolvida produz
`409 PAYMENT_ALREADY_ACTIVE`, incluindo IDs locais necessários para recuperação.

## UNKNOWN e reconciliação

Se o POST não possui resultado confiável, o attempt vira `UNKNOWN`. Novo attempt fica
proibido. Quando já existe `provider_order_id`, a reconciliação consulta o provider.
Quando o timeout ocorreu antes de receber esse ID, após cooldown o backend reenvia a
mesma tentativa com a mesma chave idempotente para descobrir/persistir o resultado.
Isso não cria outra tentativa nem autoriza outra cobrança lógica.

Cancelamento incerto fica em `CANCELLING` e segue reconciliável. O cupom não é
liberado até estado terminal confirmado.

## Validação automatizada

Em 27/09/2026, com Java 17.0.20.1 e MySQL 8.4/Testcontainers:

- Flyway aplicou e validou as 19 migrations até a V19;
- duas requisições HTTP concorrentes produziram um attempt ativo e um POST externo;
- Terminal cross-tenant e chamada sem credencial foram recusados;
- o ciclo real do cupom confirmou `RESERVED -> USED` idempotente e
  `RESERVED -> AVAILABLE` em término não aprovado;
- a suíte completa terminou com `350` testes, `0` falhas, `0` erros e `0` ignorados.
