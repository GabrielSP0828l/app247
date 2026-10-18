# Endpoints públicos

`SecurityConfig` termina em `denyAll`; somente as exceções abaixo são públicas.

| Método | Endpoint | Motivo | Rate limit / proteção | Observação |
|---|---|---|---|---|
| POST | `/auth/login`, `/auth/login/admin` | entrada de autenticação | autenticação de senha | não revela senha |
| POST | `/auth/register` | cadastro do App Cliente | limitador próprio | cria conta global `USER` |
| POST | `/auth/send-code`, `/auth/validate-code` | validação de e-mail | TTL existente + validação de corpo e escape HTML (18/10/2026) | revisão de rate limit permanece recomendada |
| POST | `/user/recuperar`, `/user/validar`, `/user/redefinir-senha` | recuperação | CPF+origem, TTL, uso único | resposta inicial anti-enumeração |
| POST | `/onboarding`, `/condominio` | bootstrap inicial | validação transacional | `/condominio` é alias legado |
| GET | `/terminal/serial/{serial}` | descoberta inicial | não autentica operação | só `terminalId`, `serialNumber`, `ativo`, `activated` |
| POST | `/webhook/mercadopago` | callback externo | assinatura/validação do provedor | idempotente |
| GET | `/public/app-config` | configuração pública | leitura | sem segredo |
| POST | `/public/leads` | captação pública | proteção própria | dados validados |
| GET | `/files/**` | imagens públicas | somente leitura | upload exige gestor |
| GET | `/`, `/actuator/health`, `/terminal/health` | documentação/saúde | sem estado sensível | health mínimo |
| GET | `/v3/api-docs/**`, `/swagger-ui/**` | contrato OpenAPI | somente leitura | pode ser desabilitado por ambiente |
| ANY | `/error` | dispatch interno de erro | sem controller de negócio | evita mascarar 404/500 |

A migração com token global (`/terminal/{id}/credential/migrate`) foi removida em 18/10/2026.

Mesmo quando o matcher HTTP permite bootstrap ou callback, o caso de uso
ainda valida seus controles próprios. Carrinho, Order, checkout, comprovante,
telemetria, sync e QR operacional não são públicos.
