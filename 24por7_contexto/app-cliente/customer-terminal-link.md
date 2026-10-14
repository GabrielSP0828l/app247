# Vínculo de cliente no Terminal por QR

## Contrato do QR

`POST /app-cliente/identificacao-tokens`, autenticado pelo JWT do cliente, retorna:

```json
{
  "qrPayload": "app247://customer-link/<opaque>",
  "expiresAt": "2026-09-30T00:00:00Z",
  "serverTime": "2026-09-29T23:58:00Z"
}
```

`opaque` contém 256 bits produzidos por `SecureRandom` e codificados em Base64 URL-safe sem padding (43 caracteres). O payload não contém CPF, `userId`, e-mail, telefone, JWT, Empresa ou Condomínio. O TTL padrão é 120 segundos, configurável por `CUSTOMER_LINK_TTL_SECONDS`. Emitir um novo QR invalida tokens ainda ativos do mesmo usuário.

O banco guarda apenas SHA-256 em `customer_identification_token.token_hash`; o valor bruto existe apenas na resposta HTTPS e no QR. Tokens não aparecem em logs.

## Consumo pelo Terminal

```http
POST /terminal/customer-link/consume
X-Terminal-Token: <device credential individual>
Content-Type: application/json

{"cartId":"<uuid>","token":"<opaque>"}
```

O body não aceita `terminalId`, `userId`, Empresa ou Condomínio. `TerminalHttpAuthenticationFilter` resolve `AuthenticatedTerminal`; a aplicação bloqueia carrinho inexistente, de outro Terminal, não aberto ou que já tenha Order/pagamento. A rota possui limite configurável por `CUSTOMER_LINK_RATE_LIMIT_PER_MINUTE` (30/minuto por Terminal por padrão).

A resposta expõe somente dados seguros para UI:

```json
{"linked":true,"displayName":"Jefferson","cartId":"<uuid>"}
```

## Atomicidade, tenant e idempotência

A consulta do hash usa `PESSIMISTIC_WRITE`. Dentro da mesma transação, o backend:

1. bloqueia o token;
2. bloqueia o carrinho;
3. compara Terminal, Empresa e Condomínio da device credential com carrinho, token e usuário;
4. valida expiração, invalidação, uso anterior e estado pré-pagamento;
5. grava `Carrinho.user`, `consumed_at` e `consumed_cart_id`.

Dois consumidores do mesmo token não têm dois sucessos. Retry do token já consumido no mesmo carrinho e para o mesmo usuário retorna sucesso idempotente; outro carrinho recebe `CUSTOMER_LINK_TOKEN_ALREADY_USED`. Mismatch de tenant não consome o token nem revela o cliente.

## Carrinho, Order, histórico e fidelidade

O vínculo autoritativo fica em `Carrinho.user`. O Terminal pode abrir um carrinho vazio apenas para o vínculo e atualizar seus itens enquanto ele continua `OPEN`. `Order(Carrinho)` copia `Carrinho.user`; nenhum `userId` vem do request de checkout. Carrinho sem cliente continua gerando Order anônima.

Depois da criação da Order/início financeiro, a identidade não pode ser trocada. A Order preserva o usuário em cancelamento e recovery. Quando chega a `PROCESSED`, as queries de `/app-cliente/compras` e `/app-cliente/compras/{id}` encontram a venda por `Order.user`; outro usuário recebe `PURCHASE_NOT_FOUND`.

`LoyaltyAccrualService` credita somente Order `PROCESSED` com usuário real. `NONE` não credita; `POINTS` e `CASHBACK` seguem a configuração existente. A chave `purchase:{orderId}` e a constraint do ledger impedem duplicidade em webhook/reconciliation repetidos.

## Erros

| Código | Condição |
|---|---|
| `CUSTOMER_LINK_TOKEN_INVALID` | formato/hash inexistente ou token invalidado |
| `CUSTOMER_LINK_TOKEN_EXPIRED` | TTL vencido pelo relógio do backend |
| `CUSTOMER_LINK_TOKEN_ALREADY_USED` | consumo anterior em outro carrinho |
| `CUSTOMER_LINK_TENANT_MISMATCH` | Empresa/Condomínio divergente |
| `CUSTOMER_NOT_LINKED_TO_CONDOMINIUM` | conta sem Condomínio |
| `CUSTOMER_ALREADY_LINKED` | carrinho vinculado a outro usuário |
| `CUSTOMER_LINK_PAYMENT_ALREADY_STARTED` | carrinho não aberto ou Order já criada |
| `CART_NOT_OWNED_BY_TERMINAL` | proteção IDOR |
| `CUSTOMER_LINK_RATE_LIMITED` | limite razoável por Terminal |

## Persistência

`V21__customer_terminal_link.sql` adiciona `carrinho.user_id` com FK tenant-aware e a tabela de tokens com hash único, TTL, invalidação, consumo e referência ao carrinho consumidor.
