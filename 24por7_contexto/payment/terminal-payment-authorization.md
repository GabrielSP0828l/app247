# Autorização do fluxo de pagamento do Terminal

## Credencial e identidade

As rotas operacionais desta etapa exigem `X-Terminal-Token`, comparado em tempo
constante com `TERMINAL_INTERNAL_TOKEN`. Rotas que não carregam o terminal no path ou
body também exigem `X-Terminal-Id`.

O backend carrega o Terminal persistido e exige:

- Terminal ativo e em lifecycle `ACTIVE`;
- Condomínio ativo;
- Empresa ativa e não encerrada;
- Carrinho e Order pertencentes exatamente ao mesmo Terminal, Condomínio e Empresa;
- configuração Mercado Pago pertencente à Empresa;
- snapshot financeiro válido, positivo e congelado;
- cupom, quando presente, reservado para aquela Order;
- ausência de outra tentativa ativa.

IDs de Carrinho, Order ou Terminal não funcionam como credencial. Recurso de outro
Terminal/tenant responde como não encontrado, sem revelar sua existência.

## Rotas protegidas

| Método e rota | Identidade adicional | Finalidade |
|---|---|---|
| `POST /carrinho` | `terminalId` no body | precificação autoritativa |
| `POST /pagamento/terminal/{cartId}` | `X-Terminal-Id` | iniciar/retomar attempt |
| `GET /pagamento/terminal/{cartId}` | `X-Terminal-Id` | alias legado, deprecated |
| `GET /order/{orderId}/status` | `terminalId` query | reconciliar Order |
| `GET /pagamento/terminal/{terminalId}/ativo` | path | recuperação após reinício |
| `POST /pagamento/terminal/order/{orderId}/cancelamento` | `terminalId` query | cancelar/reconciliar |

OpenAPI declara `terminalToken` como API key no header. Respostas nunca expõem access
token OAuth, chave idempotente do provider ou payload sensível.

## Configuração e rollout

`application.properties` mapeia
`app.terminal.internal-token=${TERMINAL_INTERNAL_TOKEN:}`; sem valor, chamadas recebem
`503 TERMINAL_AUTH_NOT_CONFIGURED`. O Compose exige a variável e `.env.example`
mantém apenas o nome vazio.

O cliente em `/home/jefiro/Documentos/projetos/TerminalPython` consultado durante a
auditoria ainda não envia `X-Terminal-Token`/`X-Terminal-Id` em `PurchaseApi.py`.
Este bloco não altera aquele projeto por decisão de escopo. O deploy precisa
provisionar a credencial e atualizar o consumidor antes de ativar esta versão do
backend; isso é coordenação de rollout, não fallback de segurança no servidor.
