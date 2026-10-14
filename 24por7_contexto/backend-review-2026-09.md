# Revisão completa do backend — setembro de 2026

> Snapshot histórico de 26/09/2026. Os achados sobre autenticação de Terminal,
> WebSocket/STOMP e origens foram corrigidos posteriormente. Para o contrato vigente,
> consulte `security/terminal-authentication.md`, `security/websocket-security.md` e
> `security/stomp-security.md`.

Data da auditoria: 26/09/2026.

Fonte primária: código Java atual, migrations `V1` a `V16`, propriedades, Docker/Compose e testes presentes no repositório. A documentação foi usada somente para comparação. O worktree já continha alterações e arquivos não rastreados antes desta revisão; nenhum código funcional, migration ou contrato foi alterado por esta auditoria.

## 1. Resumo executivo

O backend possui uma base administrativa e financeira relevante, mas ainda não está pronto para liberar o App 24/7 Cliente em produção.

- Dos 30 domínios solicitados, 12 (aproximadamente 40%) têm fluxo materialmente completo no código: Empresa, Condomínio, Produto, código de barras, os dois estoques, inventário, transferência, promoções, snapshot de `OrderItem`, integração central Mercado Pago e reconciliação.
- Os 18 restantes são parciais ou inseguros. Alguns têm bastante código, mas falham no critério ponta a ponta de entrada + regra + persistência + segurança + contrato + erro + testes.
- As 15 rotas `/app-cliente` existem no controller e têm implementação de service/persistência. Entretanto, 0/15 estão completas ponta a ponta para uma conta criada por `POST /auth/register`: o cadastro cria um `USER` global sem Empresa/Condomínio, e não existe fluxo autorizado que faça esse vínculo. Todas as rotas do App Cliente terminam em `TENANT_LINK_REQUIRED` para esse usuário.
- `cpf_verified_at`, `CustomerIdentity` e um serviço de claim existem, mas nenhuma operação marca o CPF como verificado nem chama o claim. Compras antigas por CPF, portanto, não aparecem no histórico por um fluxo executável.
- A fronteira HTTP residual é insegura: `anyRequest().permitAll()` deixa públicas rotas de usuário, carrinho, checkout, Order, pagamento, comprovante, terminal e upload. Há IDOR confirmado em `/user/**` e acesso operacional por IDs adivinháveis.
- Os WebSockets `/terminal-socket`, `/payment-socket/{terminalId}` e `/ws` aceitam qualquer origem e não autenticam o terminal/assinante.
- Cupons podem ser empilhados na mesma Order e podem ser aplicados em uma Order `CREATED`, quando uma cobrança remota já pode existir com outro valor. Pontos/cashback não são estornados no reembolso.
- O projeto principal empacota com sucesso. A suíte não compila no Java 17 por uso de `ExecutorService` em try-with-resources em `RegistrationIntegrationTest`; nenhum teste da execução atual chegou a rodar.
- A validação MySQL/Flyway real não pôde ser repetida: o teste Testcontainers está atrás da compilação quebrada e o Docker local não estava acessível no ambiente da auditoria.

Conclusão: bloquear release do App Cliente até fechar os P0 e o fluxo P1 de vínculo/verificação de conta.

## 2. Estado atual

| Domínio | Classificação | Evidência e limite atual |
|---|---|---|
| Auth | PARCIAL / INSEGURO | Login/JWT/BCrypt e cadastro cliente existem; não há verificação de CPF, revogação, refresh, issuer/audience; recuperação de senha pública não limita tentativas de código. |
| User | INSEGURO | CRUD e histórico existem, mas `/user/**` é público e opera `userId` arbitrário. |
| Empresa | IMPLEMENTADO | CRUD/lifecycle usa `EmpresaContext`; encerramento exige `ADMIN`, locks e desativa usuários. |
| Condomínio | IMPLEMENTADO | CRUD protegido por role e queries compostas com Empresa. |
| Terminal | INSEGURO | CRUD gestor é tenant-aware; ativação, bootstrap/reset, telemetria, pagamento e WebSocket operacionais não têm autenticação uniforme. |
| Produto | IMPLEMENTADO | CRUD tenant-aware, DTOs, fiscal, disponibilidade e sync. |
| Código de barras | IMPLEMENTADO | Entidade, unicidade por Empresa, validação por Produto e testes de repository. |
| EstoqueEmpresa | IMPLEMENTADO | Saldo, lock, ledger, auditoria e isolamento tenant. |
| EstoqueCondominio | IMPLEMENTADO | Disponibilidade, reserva/liberação/venda, lock e ledger. A política atual considera `ativo`, não saldo positivo. |
| Inventário | IMPLEMENTADO | Snapshot, contagem cega, conflito de versão, ajuste idempotente e auditoria. |
| Transferência | IMPLEMENTADO | Central → Condomínio transacional, locks, ledger e idempotência. |
| Promoções | IMPLEMENTADO | `PERCENTUAL`, `DESCONTO_FIXO`, `PRECO_FIXO`, menor preço, desempate, `BigDecimal` e snapshots. |
| Order | PARCIAL / INSEGURO | Totais e contexto existem; endpoints legados são públicos; não há snapshot/referência do cupom aplicado. |
| OrderItem | IMPLEMENTADO | Nome, SKU/barcode, unidade, quantidade, preços, promoção, desconto e subtotal são snapshots. A FK para Produto permanece apenas referencial. |
| Pagamento | PARCIAL / INSEGURO | Fluxo Point e estado são robustos; endpoints são públicos e identificados por carrinho/terminal/Order. |
| PaymentAttempt | PARCIAL | Modelo/schema aceitam N tentativas, mas o fluxo de produção não cria nova tentativa para a mesma Order depois da primeira; reutiliza a existente. |
| Mercado Pago | IMPLEMENTADO | OAuth, conta ativa, Point binding, create/query/cancel, assinatura de webhook, inbox, fila/retry/DLQ. Endpoints de teste ficam ativos por padrão fora de `prod`. |
| Reconciliation | IMPLEMENTADO | Startup, scheduler, GET sob demanda, retry idempotente de submissão e recuperação por terminal. `PROCESSED` não regride, salvo `REFUNDED`. |
| WebSocket | INSEGURO | Entrega after-commit funciona, mas handshake, origem e identidade do terminal não são validados. |
| Comprovante | PARCIAL / INSEGURO | Spring → FastAPI tem timeout/idempotência/correlation; `/comprovante` é público; PNG do App Cliente é um renderer separado e simples. FastAPI/n8n não estão no repositório. |
| App Cliente | PARCIAL | 15/15 rotas existem, mas cadastro não produz usuário apto a usá-las. Teste de controller mocka todo o service. |
| Identificação por QR | PARCIAL | Token 256 bits, TTL 2 min, Redis `GETDEL`, uso único e tenant checks existem; falta vínculo de conta e `IDENTITY_HASH_SECRET` no Compose. |
| Fidelidade | PARCIAL | Config, conta e ledger existem; defaults não são persistidos/configuráveis por API; reembolso não estorna. |
| Pontos | PARCIAL | Saldo + ledger e idempotência de compra/resgate; tipos reais são apenas `PURCHASE` e `REDEMPTION`; faltam estorno, ajuste e expiração. |
| Cashback | PARCIAL | `BigDecimal`, configuração, saldo e crédito existem; não há consumo nem estorno. |
| Recompensas | PARCIAL | Modelo/listagem/resgate existem; não há CRUD gestor nem validações de domínio completas. |
| Cupons | INSEGURO | Emissão/reserva/uso/liberação existem; há lock por cupom, mas não há limite de um cupom por Order nem bloqueio após criar tentativa remota. |
| Preferências | PARCIAL | GET/PUT e persistência real existem; dependem do vínculo de conta ausente e não automatizam envios. |
| Suporte | PARCIAL | Ownership, categoria, mensagem, protocolo e persistência existem; não há listagem, triagem ou atualização de status. |
| Auditoria | PARCIAL | AuditLog append-only cobre estoque, produto, promoções, inventário, MP e lifecycle; não cobre auth, usuário, App Cliente, cupons, resgates e suporte. |

## 3. Funcionalidades completas

### Hierarquia e tenant administrativo

- Empresa é a fronteira de tenant e o JWT é conferido contra usuário/Empresa atuais.
- CRUDs de Empresa, Condomínio e Terminal administrativo usam `EmpresaContext` e queries tenant-aware.
- O encerramento definitivo de Empresa exige `ADMIN`, lock, ausência de pagamentos ativos, unlink do Mercado Pago, reset dos Terminais e desativação de usuários.

### Catálogo, estoque, inventário e transferência

- Produto e código de barras possuem unicidade por Empresa e validação de ownership.
- Estoque central e por Condomínio usam `BigDecimal`, versionamento, locks e ledger.
- Inventário e transferência possuem fluxos transacionais e testes específicos no código.

### Promoções e snapshots de venda

- Os três tipos de promoção são calculados com `BigDecimal`.
- Promoções não acumulam: vence o menor preço; empate usa escopo Condomínio, prioridade e ID.
- `CartItem` e `OrderItem` congelam preço original/aplicado, promoção, desconto e subtotal.

### Mercado Pago e reconciliação

- `PaymentAttempt` persiste `provider_order_id`, `provider_payment_id`, `external_reference` e chave de idempotência.
- Webhook tem HMAC, deduplicação persistente/Redis, fila de processamento, retry e DLQ.
- Reconciliação ocorre no startup, scheduler e consultas do Terminal.
- Cancelamento remoto só confirma cancelamento após resposta autoritativa; timeout mantém o estado pendente/bloqueado, não inventa cancelamento.
- A máquina de estados impede regressão de `PROCESSED` para estados anteriores; só admite `REFUNDED`.

## 4. Funcionalidades parciais

### Cadastro do App Cliente

`POST /auth/register` existe e cria conta real:

- role forçada para `USER`;
- BCrypt;
- CPF normalizado e validado matematicamente;
- e-mail validado e normalizado;
- duplicidade de CPF/e-mail tratada por checks e constraints;
- erro estruturado;
- rota pública explícita;
- DTO não aceita `role`, `empresaId` ou `condominioId` e campos JSON extras não promovem/vinculam o usuário;
- rate limit Redis com fallback local.

Limites: a conta nasce sem Empresa/Condomínio, `cpfVerifiedAt=null` e não existe onboarding cliente posterior. O teste de integração existe, mas atualmente nem compila no Java 17.

### CPF e compras antigas

Existem três conceitos no modelo, porém só dois são alcançáveis:

- CPF sintaticamente válido: implementado por `CpfService`;
- CPF pertencente ao `User`: implícito no cadastro/login;
- CPF verificado: coluna `cpf_verified_at` existe, mas nenhum fluxo a preenche.

`CustomerIdentity` guarda HMAC do CPF por Empresa, `claimed_user_id` e `claimed_at`. Carrinho/Order podem referenciar a identidade. `HistoricalPurchaseOwnershipService.claimVerified` exige `cpfVerifiedAt` e tenant, mas não é chamado por nenhum controller, listener ou service. A query de histórico aceita identidade reivindicada com user/Empresa corretos, portanto o desenho evita busca global apenas por CPF, mas o fluxo é inoperante.

### Catálogo App Cliente

Deriva corretamente:

```text
principal autenticado → User.condominio → EstoqueCondominio ativo
→ Produto ativo → PricingService
```

O app não precisa enviar `terminalId` nem `condominioId`, o que é correto para ownership. A paginação, porém, carrega todo o catálogo na memória antes de recortar. A política atual permite item ativo com saldo zero/negativo; isso está explícito no código/documentação e precisa de confirmação de produto.

### PaymentAttempt

Schema e entidade aceitam vários attempts por Order. A cobrança Point cria apenas um attempt quando `order.getPagamento()==null`; uma Order já existente mantém o attempt mais recente e não há caso de uso que encerre o antigo e crie o próximo. Logo “várias tentativas por Order” existe no modelo/teste SQL, não no fluxo produtivo.

Estados reais:

- Order: `PENDING`, `CREATED`, `AT_TERMINAL`, `PROCESSED`, `CANCELED`, `EXPIRED`, `FAILED`, `REFUNDED`, `ACTION_REQUIRED`;
- PaymentAttempt: `PENDING`, `PROCESSED`, `CANCELED`, `EXPIRED`, `FAILED`, `REFUNDED`, `ACTION_REQUIRED`.

Não existem estados `PROCESSING`, `APPROVED`, `REJECTED` ou `UNKNOWN` nessas entidades. A resposta ao Terminal traduz para `WAITING_PAYMENT`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `ACTION_REQUIRED`, `REFUNDED`.

### Comprovantes

- Envio valida Order + Terminal e exige Order/PaymentAttempt aprovados.
- Snapshot usa nome/subtotal de `OrderItem`, `paidAt` e timezone `America/Bahia`.
- Canal/destino são validados; request contém `request_id`; headers levam idempotência e correlation ID; timeouts são configurados.
- O endpoint App Cliente aplica ownership antes de chamar o envio.

Limites:

- `POST /comprovante` legado é público e usa apenas `orderId` + `terminalId` como posse.
- `GET /app-cliente/compras/{id}/comprovante` gera outro PNG em Java, usando `createdAt`, sem o snapshot/layout FastAPI e sem persistir artefato.
- O endpoint de envio responde `202`, mas executa a chamada externa de forma síncrona antes da resposta.
- O código FastAPI e o workflow n8n não estão no repositório; deduplicação e entrega ponta a ponta não foram verificadas.

### Fidelidade, pontos e cashback

- Somente `Order.PROCESSED` com `order.user` direto gera benefício. Identidade histórica/CPF sem conta não gera benefício retroativo, como requerido.
- Pontos usam `long`; cashback usa `BigDecimal`.
- Crédito de compra é idempotente por `purchase:{orderId}`; resgate usa lock da conta e `Idempotency-Key`.

Limites: não há estorno no `REFUNDED`, expiração, ajuste, consumo de cashback nem enum/constraint do tipo de movimento.

## 5. Funcionalidades ausentes

- Verificação real de CPF e operação que preencha `cpfVerifiedAt`.
- Vínculo autorizado da conta global a Empresa/Condomínio.
- Invocação do claim de compras históricas após CPF verificado.
- CRUD gestor de `AppClienteConfig`.
- CRUD gestor de recompensas.
- Snapshot/referência do cupom aplicado na Order.
- Regra de um cupom por Order.
- Estorno de pontos/cashback após reembolso e movimentos `ESTORNO`, `AJUSTE`, `EXPIRACAO`.
- Uso/debito de cashback.
- Fluxo operacional de suporte após abertura do protocolo.
- Refresh/revogação de JWT.
- Autenticação de Terminal unificada para REST/WebSocket.
- Teste E2E real Spring → FastAPI → n8n.

## 6. Problemas de segurança

### P0 — fronteira HTTP residual pública

`SecurityConfig.anyRequest().permitAll()` deixa públicas, entre outras:

- `/user/**`;
- `/carrinho/**`;
- `/checkout/**`;
- `/order/**`;
- `/pagamento/**`;
- `/comprovante/**`;
- `/terminal/**` (singular: ativação, lifecycle, telemetria e QR consumers; estes últimos têm segredo próprio no service);
- `/files/upload`;
- `/api/testes/mercadopago/**`.

### P0 — IDOR de usuário

- `GET /user/{userId}` retorna CPF/e-mail/telefone de qualquer ID.
- `GET /user/{userId}/orders` lista compras sem principal/tenant.
- `PUT /user/{id}`, upload de foto por `id` e alteração de senha por `request.userId` não verificam principal/Empresa.

### P0 — checkout e pagamentos por IDs

- `GET /checkout?id={userId}&session={sessionId}` vincula qualquer userId a uma sessão sem autenticação/tenant.
- Order, status, cobrança, recuperação ativa e cancelamento são públicos; conhecer carrinho/Order/terminal funciona como credencial.
- `POST /comprovante` pode disparar envio externo para destino escolhido por quem conhece Order + Terminal.

### P0 — WebSocket

- qualquer cliente pode enviar heartbeat com `terminalId` arbitrário em `/terminal-socket`;
- qualquer cliente pode conectar a `/payment-socket/{terminalId}`, substituir a sessão legítima em memória e receber estado de pagamento/reset;
- qualquer cliente pode assinar `/topic/payment/{terminalId}` via STOMP;
- todos aceitam origem `*` e não validam credencial de Terminal.

### P0 — recuperação de senha

O código de seis dígitos pode ser testado sem autenticação e sem rate limit/contador de falhas em `/user/validar`. Isso permite brute force do código durante o TTL. `verificarCode`/`novaSenha` também não normalizam CPF de forma consistente.

### P0 — cupons e valor cobrado

- `TerminalQrService.applyCoupon` aceita Order `CREATED`; uma Order remota já pode ter sido criada com valor anterior.
- Não há campo/constraint que limite uma reserva por Order. Cupons diferentes podem ser aplicados sequencialmente e acumulados.
- O `PaymentAttempt.amount_requested` não é atualizado/invalidado ao aplicar cupom; isso pode divergir de `Order.totalCobrado`.

### Outros riscos

- JWT inválido lança `RuntimeException` e tende a produzir 500, não 401.
- Token JWT dura 200 horas e não tem issuer/audience/revogação.
- `POST /auth/send-code` não possui rate limit e pode ser usado para spam.
- `X-Forwarded-For` é aceito diretamente pelo rate limiter de cadastro.
- Endpoints de teste Mercado Pago ficam habilitados por padrão em `application.properties`; Compose de produção os desabilita.
- Swagger/OpenAPI não declara esquema Bearer explícito e permanece público.

## 7. Problemas de banco

As entidades atuais possuem colunas correspondentes nas migrations `V1`–`V16` por inspeção estática. Não foi encontrada coluna JPA obviamente ausente na cadeia. Ainda assim, há gaps de integridade:

- `customer_coupon.reserved_order_id` referencia apenas `orders(id)`, não `(id, empresa_id)`.
- `loyalty_movement.order_id` referencia apenas `orders(id)` e a tabela não carrega `empresa_id` para uma FK composta.
- `reserved_order_id` não é único; o banco permite vários cupons reservados para a mesma Order.
- `reward_redemption.coupon_id` não é `UNIQUE`, embora a entidade modele a relação como `@OneToOne`; o banco permite associar o mesmo cupom a múltiplos resgates.
- Não há check constraints para `points_per_currency`, `cashback_percent`, `custo_pontos`, valores/mínimos/máximos de recompensa/cupom e coerência de deltas do ledger.
- `customer_coupon.tipo` não tem check constraint.
- A Order não possui `coupon_id` nem snapshot de regras/valor do cupom; o desconto fica agregado em `desconto`.
- `V16` normaliza CPF/e-mail antes de aplicar alterações. Dados legados que colidam após normalização podem derrubar a migration; não há preflight na cadeia.
- `V14` usa `TIMESTAMP` sem precisão para intenção de cancelamento, divergindo do padrão `DATETIME(6)` dos demais instantes.
- Faltam índices orientados às consultas de recompensas por Empresa/Condomínio/validade e movimentos por conta/data.

Migrations funcionais futuras serão necessárias para: cupom único/snapshot na Order, FKs tenant-aware restantes, constraints financeiras e eventuais estados/ledger adicionais. Nenhuma migration foi criada nesta auditoria.

## 8. Problemas de API

- Cadastro e App Cliente não formam um fluxo: não há endpoint de vínculo Empresa/Condomínio nem verificação de CPF.
- Não há endpoints gestores para configuração App Cliente ou recompensas.
- `GET /app-cliente/catalogo` faz paginação em memória.
- `GET /app-cliente/compras` inclui identidade histórica somente depois de um claim que nunca é executado.
- `GET` é usado para operações com efeitos (`/order/finalizar`, `/pagamento/terminal/{carrinho}`, `/checkout` para vínculo).
- Erros não cobertos por `RestExceptionHandler` viram respostas Spring/500 inconsistentes.
- A documentação `api.md` e `autenticacao.md` ainda descreve o contrato antigo de `/auth/register`, divergente do controller atual.
- Não há frontend no repositório. “Usado pelo frontend” abaixo significa contrato informado nesta tarefa; o consumo real não pôde ser inspecionado.

## 9. Problemas de integração

- `app247.identity.hash-secret=${IDENTITY_HASH_SECRET:}` existe, mas `../docker-compose.test.yml` não repassa `IDENTITY_HASH_SECRET`; carrinho com CPF falha fechado com 503 no Compose atual.
- O Compose não repassa URLs/versões de Termos e Privacidade; o cadastro permite seguir sem aceite quando a configuração está vazia.
- `COMPROVANTE_INTERNAL_TOKEN` é opcional na API principal, embora obrigatório no container FastAPI; configuração divergente pode gerar falha de integração.
- FastAPI e n8n são imagens/serviços externos ao repositório; contratos foram auditados apenas do lado Spring/Compose.
- Não foi feito teste remoto Mercado Pago, FastAPI ou n8n por falta de credenciais/ambiente autorizado.

## 10. Testes faltantes

Inventário encontrado: 71 classes de teste e 276 métodos/factories anotados. Há boa cobertura nominal em estoque, inventário, transferência, pricing, Mercado Pago, reconciliação, telemetry e comprovante.

Resultado real desta auditoria:

- `./mvnw test`: não executou porque `mvnw` não tem permissão de execução;
- `bash mvnw test`: falhou em `testCompile` em `RegistrationIntegrationTest:115`; `ExecutorService` não implementa `AutoCloseable` no Java 17;
- `bash mvnw -Dmaven.test.skip=true package`: sucesso, JAR gerado;
- Testcontainers MySQL/Flyway: não executado, pois a compilação de testes falhou e o Docker não estava acessível.

Gaps de cobertura:

- `AppClienteEndpointsIntegrationTest` mocka `AppClienteService`; não testa JPA, ownership, tenant, Redis, comprovante ou fluxo real.
- Não há teste direto de `AppClienteService`, `CouponLifecycleService`, `LoyaltyAccrualService`, `RegistrationService`, `CustomerIdentityService` ou `TerminalQrService` com persistência real.
- Não há E2E cadastro → login → vínculo → bootstrap → catálogo → histórico.
- Não há teste de verificação CPF/claim porque o fluxo não existe.
- Não há concorrência real para dois cupons/uma Order, resgate simultâneo, criação concorrente de `LoyaltyAccount` ou refund/reversal.
- Não há testes que exijam bloqueio das rotas legadas públicas/IDOR.
- WebSocket testa roteamento, mas não segurança de handshake/tenant.
- Comprovante testa RestTemplate mockado, não FastAPI/n8n real.

## 11. Matriz dos endpoints

Legenda: “parcial” em funcionalidade significa que o método tem implementação real, mas o fluxo do usuário criado pelo cadastro não alcança a rota ou há gap do domínio. “Frontend” é a expectativa fornecida; o consumidor não está neste repositório.

| Método | Endpoint | Existe / Controller | Service | Repository/query | Funcional | Auth | Ownership | Tenant | DTO/validação | Testes atuais | Frontend | Pendência principal |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/app-cliente/bootstrap` | Sim / `AppClienteController` | `AppClienteService.bootstrap` | User + config | Parcial | `ROLE_USER` | Principal | Empresa + Condomínio | Response record | Controller com service mockado | Esperado | Cadastro não vincula tenant |
| GET | `/app-cliente/catalogo` | Sim | `catalog` + `PricingService` | `findCurrentCatalog`, promoções | Parcial | `ROLE_USER` | Principal | Condomínio derivado | Query params + paginação validada | Controller mock + pricing unitário | Esperado | Vínculo ausente; paginação em memória |
| GET | `/app-cliente/compras` | Sim | `purchases` | `findOwnedPage` | Parcial | `ROLE_USER` | User ou identity claimed | Empresa obrigatória | Page response | Controller mockado | Esperado | Claim histórico nunca executado |
| GET | `/app-cliente/compras/{id}` | Sim | `purchase` | `findOwnedDetail` | Parcial | `ROLE_USER` | User/identity + 404 opaco | Empresa obrigatória | Response record | Controller mockado | Esperado | Sem teste JPA real |
| GET | `/app-cliente/compras/{id}/comprovante` | Sim | `receipt` | Order owned query | Parcial | `ROLE_USER` | Sim | Sim | PNG | Controller mockado | Esperado | Renderer separado e sem teste real |
| POST | `/app-cliente/compras/{id}/comprovante/envios` | Sim | `sendReceipt` → `ComprovanteService` | Order + Redis | Parcial | `ROLE_USER` | Sim | Sim | `canal`/`destinatario` obrigatórios | Controller mock; integração externa mockada | Esperado | E2E FastAPI/n8n ausente |
| POST | `/app-cliente/identificacao-tokens` | Sim | `identificationToken` | Redis TTL | Parcial | `ROLE_USER` | Principal | User/Empresa/Condomínio no valor Redis | Token response | Controller/Terminal services mockados | Esperado | Conta/vínculo e secret do Compose |
| GET | `/app-cliente/fidelidade` | Sim | `loyalty` | Account + movement | Parcial | `ROLE_USER` | Principal | Empresa | Response record | Sem teste do service | Esperado | Sem reversal/expiry/adjustment |
| GET | `/app-cliente/recompensas` | Sim | `rewardList` | Reward por Empresa/Condomínio | Parcial | `ROLE_USER` | Principal | Sim | Response record | Controller mockado | Esperado | Sem CRUD gestor/validação |
| POST | `/app-cliente/recompensas/{id}/resgates` | Sim | `redeem` | Account lock + redemption/coupon/movement | Parcial | `ROLE_USER` | Principal | Reward + conta por Empresa | Header `Idempotency-Key` | Controller mockado; sem concorrência real | Esperado | Constraints/testes financeiros |
| GET | `/app-cliente/cupons` | Sim | `couponList` | Coupon por user + Empresa | Parcial | `ROLE_USER` | Sim | Sim | Response record | Controller mockado | Esperado | Sem teste lifecycle real |
| POST | `/app-cliente/cupons/{id}/tokens` | Sim | `couponToken` | Coupon owned + Redis | Parcial | `ROLE_USER` | ID + user | Empresa | Path + token response | Controller mockado | Esperado | Aplicação permite stack/Order CREATED |
| GET | `/app-cliente/preferencias` | Sim | `getPreferences` | Preference por user + Empresa | Parcial | `ROLE_USER` | Principal | Sim | Response record | Controller mockado | Esperado | Vínculo ausente; efeito automático ausente |
| PUT | `/app-cliente/preferencias` | Sim | `putPreferences` | Preference upsert | Parcial | `ROLE_USER` | Principal | Sim | Booleans `@NotNull` | Controller mockado | Esperado | Sem teste de persistência/concorrência |
| POST | `/app-cliente/compras/{id}/suporte` | Sim | `support` | Owned Order + support | Parcial | `ROLE_USER` | Sim | Sim | categoria/mensagem validadas | Controller mockado | Esperado | Sem triagem/listagem/status |
| POST | `/auth/register` | Sim / `AuthController` | `RegistrationService` | UserRepository | Sim para criar conta; parcial para App Cliente | `permitAll` explícito | N/A | Sem tenant por desenho | Bean Validation + erros estruturados | Integração existe, mas não compila em Java 17 | Esperado | Não verifica CPF nem vincula Empresa/Condomínio |

Resumo das rotas App Cliente: 15/15 presentes no código; 0 completas ponta a ponta para usuário recém-cadastrado; 15 parciais; 0 ausentes.

## 12. Pendências P0

1. Fechar a política residual `permitAll` e autenticar rotas operacionais de Terminal.
2. Eliminar IDOR em `/user/**` e derivar o usuário do principal.
3. Autenticar e autorizar os três canais WebSocket por Terminal/tenant.
4. Impedir vínculo arbitrário de user em `/checkout` e proteger Order/pagamento/comprovante por credencial do Terminal.
5. Adicionar rate limit/contador de tentativas e consumo seguro na validação de recuperação de senha.
6. Impedir cupom após criação de PaymentAttempt/Order remota e limitar uma reserva por Order.
7. Implementar estorno idempotente de pontos/cashback em `REFUNDED`.

## 13. Pendências P1

1. Implementar vínculo autorizado conta → Empresa/Condomínio.
2. Implementar verificação de CPF e invocar claim de compras antigas.
3. Configurar `IDENTITY_HASH_SECRET` no runtime e validar fail-fast no startup.
4. Corrigir a compilação da suíte Java 17 e restabelecer o gate de testes.
5. Criar testes reais de service/repository para todas as rotas App Cliente.
6. Criar CRUD gestor de configuração App Cliente e recompensas com validações financeiras.
7. Fortalecer migrations de cupom/loyalty/reward e adicionar snapshot do cupom na Order.
8. Definir e implementar política real de múltiplos PaymentAttempts ou impedir o modelo enganoso.
9. Proteger envio legado de comprovante e unificar a visualização com o snapshot aprovado.

## 14. Pendências P2

1. Completar ledger com tipos explícitos, ajuste, expiração e relatórios.
2. Implementar uso de cashback.
3. Completar workflow de suporte.
4. Configurar Termos/Privacidade reais e tornar o aceite obrigatório quando o produto exigir.
5. Ampliar AuditLog para identidade, fidelidade, cupons, suporte e ações sensíveis.
6. Executar testes E2E Spring → FastAPI → n8n e validar deduplicação externa.
7. Adicionar OpenAPI Bearer e documentar erros/roles atuais.
8. Mover paginação/filtros do catálogo para o banco e adicionar índices de consulta.

## 15. Pendências P3

1. Reduzir TTL do JWT e avaliar issuer, audience, refresh e revogação.
2. Uniformizar injeção/formatos e remover endpoints/aliases legados após migração dos consumidores.
3. Persistir/propagar correlation ID de forma transversal.
4. Melhorar respostas de erro de JWT e exceções não mapeadas.
5. Revisar a política de saldo zero/negativo como disponibilidade de catálogo.

## 16. Ordem recomendada de implementação

### P0 — antes de qualquer release

1. Fechar REST/IDOR e introduzir credencial de Terminal nos endpoints operacionais.
2. Autenticar WebSocket e impedir sequestro de sessão/tópico.
3. Corrigir recuperação de senha contra brute force.
4. Corrigir integridade cupom ↔ Order ↔ PaymentAttempt e impedir empilhamento.
5. Implementar estorno financeiro de fidelidade em reembolso.

### P1 — desbloquear App Cliente

6. Fazer a suíte compilar e voltar a executar o gate completo, incluindo MySQL/Flyway.
7. Implementar vínculo autorizado de conta a Empresa/Condomínio.
8. Implementar verificação de CPF e claim tenant-aware de compras antigas.
9. Configurar secret de identidade e legal documents no runtime.
10. Validar com E2E real: cadastro → login → vínculo/verificação → bootstrap → catálogo → identificação → compra → histórico/comprovante.

### P1/P2 — completar produto

11. Criar gestão de configuração/recompensas e migrations de integridade.
12. Fechar loyalty/cashback/rewards/coupons com concorrência e reversal.
13. Completar preferências, suporte, auditoria e comprovante externo.
14. Somente então otimizar catálogo, OpenAPI, JWT e remover legado.
