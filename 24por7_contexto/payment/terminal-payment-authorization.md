# Autorização do fluxo de pagamento do Terminal

## Expansão do canal autenticado — 27/09/2026

O mesmo `X-Terminal-Token` protege sync de produtos, telemetria,
lifecycle/factory reset, envio de comprovante, `/payment-socket/{terminalId}`,
`/terminal-socket` e `/ws`. Seu valor operacional é a credencial individual do
equipamento, armazenada somente por hash e sujeita a rotação/revogação própria.

`TERMINAL_INTERNAL_TOKEN` é apenas migração explícita e nunca fallback operacional.
Detalhes em [[../security/terminal-authentication]].

## Credencial e identidade

As rotas operacionais desta etapa exigem `X-Terminal-Token`; o hash resolve a
credencial individual para exatamente um Terminal. Rotas legadas que não carregam o
Terminal no path/body ainda usam `X-Terminal-Id`, mas ele é somente uma restrição e
precisa coincidir com o Principal resolvido.

O backend carrega o Terminal persistido e exige:

- Terminal ativo e em lifecycle `ACTIVE`;
- Condomínio ativo;
- Empresa ativa e não encerrada;
- Carrinho e Order pertencentes exatamente ao mesmo Terminal, Condomínio e Empresa;
- configuração Mercado Pago pertencente à Empresa;
- snapshot financeiro válido, positivo e congelado;
- cupom, quando presente, reservado para aquela Order;
- ausência de outra tentativa ativa.

IDs de Carrinho, Order ou Terminal não funcionam como credencial. Recurso financeiro
de outro tenant continua respondendo como não encontrado depois de estabelecida a
identidade; tentativa de assumir outro `terminalId` é recusada antes com `403`.

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

`application.properties` mantém `TERMINAL_INTERNAL_TOKEN` somente para migração e
`TERMINAL_LEGACY_AUTH_ENABLED=false` por padrão. O Compose exige origins WebSocket
explícitas, mas não exige o segredo global quando a migração está desligada.

O cliente em `/home/jefiro/Documentos/projetos/TerminalPython` consultado durante a
auditoria ainda não recebe/armazena a device credential nem a envia nos canais.
Este bloco não altera aquele projeto por decisão de escopo. O deploy precisa
provisionar a credencial individual e atualizar o consumidor antes de ativar esta versão do
backend; isso é coordenação de rollout, não fallback de segurança no servidor.
