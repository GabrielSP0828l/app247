# Configuração do App Cliente por Empresa

## Modelo

`app_cliente_config` possui no máximo uma linha por Empresa: `empresa_id` é PK e FK
para `empresa(id)`. Não existe configuração global nem seleção de tenant no request.
As APIs administrativas obtêm a Empresa do JWT por `EmpresaContext`.

A mesma entidade `AppClienteConfig` foi mantida para evitar duplicar o domínio já
existente. A V18 migra as linhas antigas por Condomínio para uma linha consolidada por
Empresa. Quando havia divergência entre condomínios, a consolidação é conservadora:
uma feature permanece ativa somente quando estava ativa em todas as linhas, e um modo
de fidelidade divergente vira `NONE`.

Empresa sem linha explícita recebe defaults seguros, sem gravação implícita:

- catálogo, promoções, fidelidade, QR, preferências e suporte desativados;
- modo de fidelidade `NONE`;
- pontos, cashback, recompensas e cupons desativados.

## API do gestor

Ambas as rotas exigem JWT `ADMIN` ou `GERENTE`:

- `GET /admin/app-cliente/config`;
- `PUT /admin/app-cliente/config`.

O `PUT` recebe somente:

```json
{
  "catalogoAtivo": true,
  "promocoesAtivas": true,
  "fidelidadeAtiva": true,
  "qrIdentificacaoAtivo": true,
  "preferenciasAtivas": false,
  "suporteAtivo": false
}
```

Não há `empresaId` nem `condominioId`. Para ativar `fidelidadeAtiva`, o gestor deve
ter configurado antes um modo financeiro válido em `/admin/app-cliente/fidelidade`.
Caso contrário, a API responde `400 INVALID_LOYALTY_CONFIG`.

## Flags efetivas no cliente

`GET /app-cliente/bootstrap` calcula as flags efetivas no backend:

- promoções exigem catálogo ativo;
- fidelidade exige configuração ativa e modo válido;
- `POINTS` ativa pontos, recompensas e cupons, e desativa cashback;
- `CASHBACK` ativa cashback, e desativa pontos, recompensas e cupons;
- fidelidade desligada desativa todas as flags derivadas.

As flags não são hardcoded no DTO. Desativar uma feature não apaga saldo, movimentos,
recompensas ou cupons. Reativar reutiliza os dados persistidos.

## Política de feature desativada

Endpoints operacionais de uma feature desligada respondem `409` com código de
domínio, como `CATALOG_DISABLED`, `REWARDS_DISABLED`, `COUPONS_DISABLED`,
`CUSTOMER_QR_DISABLED`, `PREFERENCES_DISABLED` ou `SUPPORT_DISABLED`. O endpoint
`GET /app-cliente/fidelidade` preserva seu contrato de leitura e responde
`enabled=false`, `mode=NONE` em vez de falhar.
