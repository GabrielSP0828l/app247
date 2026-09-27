# Recompensas de fidelidade

## Modelo

`Reward` pertence obrigatoriamente à Empresa. O campo legado opcional de Condomínio é
mantido somente para compatibilidade de dados; recompensas criadas pela nova API são
da Empresa inteira. A consulta do cliente aceita recompensa global ou do Condomínio
atual, sempre dentro da Empresa autenticada.

Campos administráveis:

- nome e descrição;
- `tipoDesconto`: `PERCENTUAL` ou `VALOR_FIXO`;
- `valorDesconto`;
- `valorMinimoCompra`;
- `descontoMaximo` opcional;
- `custoPontos`;
- `validadeDias`;
- `limiteUsoPorUsuario`;
- `ativo`.

Valores financeiros usam `BigDecimal`/`DECIMAL(15,6)`. O caso de referência é
suportado integralmente: `10% OFF`, 500 pontos, compra mínima de R$ 100 e desconto
máximo de R$ 25.

## CRUD do gestor

As rotas exigem `ADMIN` ou `GERENTE` e derivam a Empresa do JWT:

- `GET /admin/app-cliente/recompensas?page=0&size=20&ativo=true`;
- `POST /admin/app-cliente/recompensas`;
- `GET /admin/app-cliente/recompensas/{id}`;
- `PUT /admin/app-cliente/recompensas/{id}`;
- `DELETE /admin/app-cliente/recompensas/{id}`.

O `DELETE` é sempre soft delete (`ativo=false`) para preservar emissões e auditoria.
Recurso de outro tenant é tratado como inexistente (`404 REWARD_NOT_FOUND`), sem
revelar ownership.

## Validações

- nome obrigatório, com até 120 caracteres; descrição com até 1000;
- custo maior que zero e com teto técnico de 1 trilhão;
- validade entre 1 e 3650 dias;
- mínimo maior ou igual a zero;
- valor de desconto maior que zero;
- percentual limitado a 100;
- desconto máximo, quando presente, maior que zero;
- limite por usuário entre 1 e 1.000.000.

O banco repete as invariantes críticas com `CHECK` e mantém FK composta para impedir
Condomínio de outra Empresa.

## App Cliente e resgate

`GET /app-cliente/recompensas` expõe somente registros ativos, não expirados, do
tenant atual e apenas quando a fidelidade efetiva está em `POINTS`. A resposta calcula
`eligible` e `unavailableReason` usando saldo e limite de resgates; o Flutter não toma
essa decisão.

O endpoint existente `POST /app-cliente/recompensas/{id}/resgates` foi preservado. Ele
valida tenant, modo, atividade, validade, Condomínio legado, limite por usuário,
saldo e idempotência antes de emitir o cupom e debitar pontos na mesma transação.
