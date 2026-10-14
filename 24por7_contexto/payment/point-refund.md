# Reembolso integral Mercado Pago Point

Voltar para [[00-index]]. Integração em [[mercado-pago]], reconciliação em
[[payment-reconciliation]] e fidelidade em [[app-cliente/loyalty-config]].

## Contrato administrativo

`POST /admin/orders/{orderId}/refund` exige JWT com papel `ADMIN` ou `GERENTE`.
A Empresa é derivada do principal autenticado e a busca da Order combina
`orderId + empresaId` sob lock pessimista; uma Order de outro tenant responde como
inexistente. O endpoint não recebe valor: o fluxo implementado é exclusivamente o
reembolso integral suportado por Point Orders.

A Order e sua tentativa mais recente precisam estar `PROCESSED` e possuir o ID da
order remota. Repetir a operação depois de `REFUNDED` devolve sucesso idempotente.
Estados diferentes são recusados com `REFUND_NOT_ALLOWED`.

Resposta segura:

```json
{
  "orderId": "uuid",
  "status": "refunded",
  "amount": 13.45,
  "requestedAt": "2026-09-30T13:00:00Z",
  "confirmedAt": "2026-09-30T13:00:01Z",
  "confirmed": true
}
```

## Comando remoto e resultado incerto

Antes da rede, `PaymentRefundPersistenceService` persiste `refund_requested_at` e
uma `refund_idempotency_key` aleatória, única e estável. A integração então executa
`POST /v1/orders/{providerOrderId}/refund`, com body vazio, Bearer da conta OAuth da
Empresa e `X-Idempotency-Key` persistida. Token OAuth e chave não são registrados.

HTTP 201 `refunded` passa pela mesma `PaymentStateTransitionService` usada por
webhook e reconciliação, preenchendo `refund_confirmed_at` e, quando disponível,
`provider_refund_id`. Timeout ou resposta ambígua não inventa sucesso: o serviço
consulta a order remota imediatamente e, se ainda não houver confirmação, devolve
erro sanitizado. Startup/scheduler continuam consultando tentativas `PROCESSED` com
intenção de reembolso pendente, portanto uma queda entre o provedor e o commit local
é recuperável.

## Efeitos locais

Uma única transição `PROCESSED -> REFUNDED`:

- muda Order e PaymentAttempt para `REFUNDED`;
- encerra o carrinho sem apagar a associação histórica ao cliente;
- libera/reverte estoque e cupom pelos listeners idempotentes existentes;
- cria auditoria de solicitação e confirmação;
- publica o estado `REFUNDED` para o Terminal;
- cria no ledger o movimento `REFUND_REVERSAL` com chave
  `refund:{orderId}`.

O movimento de fidelidade é o inverso exato do crédito `purchase:{orderId}` para
POINTS ou CASHBACK. A conta é bloqueada pessimisticamente e a chave também possui
unicidade no banco. Se o cliente já gastou o benefício, o saldo pode ficar negativo;
créditos futuros compensam a dívida. `LoyaltyMode.NONE` ou compra sem usuário não
produzem movimento.

Reentrega de webhook, retry HTTP e reconciliação repetida não duplicam estoque,
cupom nem fidelidade. Reembolso parcial não faz parte deste contrato.
