# Configuração de fidelidade

## Persistência e modos

A configuração financeira continua na tabela e entidade `app_cliente_config`, agora
1:1 com Empresa. Uma entidade `FidelidadeConfig` paralela não foi criada porque modo,
taxas e flags já faziam parte do modelo executável.

Os únicos modos aceitos são:

- `NONE`: fidelidade inativa, taxas nulas;
- `POINTS`: `pontosPorReal > 0`, `cashbackPercentual = null`;
- `CASHBACK`: `pontosPorReal = null`, `0 < cashbackPercentual <= 100`.

`POINTS_AND_CASHBACK` não existe. Taxas usam `BigDecimal`; o banco usa
`DECIMAL(15,6)` para pontos por real e `DECIMAL(9,6)` para percentual de cashback.
As invariantes são validadas no service e por `CHECK` no MySQL.

## API do gestor

Rotas `ADMIN`/`GERENTE`, sempre no tenant do JWT:

- `GET /admin/app-cliente/fidelidade`;
- `PUT /admin/app-cliente/fidelidade`.

Exemplo de pontos:

```json
{
  "ativo": true,
  "modo": "POINTS",
  "pontosPorReal": "1.000000",
  "cashbackPercentual": null
}
```

Exemplo de cashback:

```json
{
  "ativo": true,
  "modo": "CASHBACK",
  "pontosPorReal": null,
  "cashbackPercentual": "2.000000"
}
```

Modo desconhecido retorna `400 INVALID_LOYALTY_MODE`; combinação inválida retorna
`400 INVALID_LOYALTY_CONFIG`.

## Consumo e preservação de dados

`GET /app-cliente/fidelidade` lê a configuração da Empresa autenticada. Nunca expõe
`POINTS` quando a Empresa está em cashback ou a fidelidade está desligada. O crédito
de compra em `LoyaltyAccrualService` usa a mesma configuração por Empresa.

Desativar fidelidade apenas suspende crédito, exposição e resgate. `LoyaltyAccount`,
saldo e `LoyaltyMovement` permanecem. Não foi criado ledger novo nem consumo de
cashback nesta etapa.
