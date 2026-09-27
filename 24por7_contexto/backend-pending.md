# Backend pending — backlog objetivo

Backlog derivado da auditoria de 26/09/2026. Os itens não autorizam implementação automática; servem como sequência verificável de trabalho.

## [P0] Proteger Order e pagamento operacional com credencial de Terminal

Status:
PARCIAL — fluxo financeiro do Terminal protegido no P1-C

Tipo:
SECURITY / API / BACKEND

Resolvido no P1-C:
`POST /carrinho`, início/retomada Point, status da Order, recuperação ativa e
cancelamento agora exigem `X-Terminal-Token`, carregam o Terminal persistido e validam
Terminal/Condomínio/Empresa/Carrinho/Order. Testes reais cobrem ausência de credencial,
concorrência e cross-tenant.

Pendente:
`GET /carrinho/{id}`, `GET /order/finalizar`, `GET /order` e `/checkout/**` continuam
sob a regra residual pública. Não fazem o POST remoto nesta implementação, mas ainda
expõem/criam estado operacional por UUID.

Impacto:
Leitura, criação, reconciliação ou cancelamento de compra/pagamento por cliente não autorizado; exposição de dados financeiros.

Arquivos relacionados:
- `infra/security/SecurityConfig.java`
- `infra/controller/CarrinhoController.java`
- `infra/controller/OrderController.java`
- `infra/controller/PagamentoController.java`
- `infra/service/OrderService.java`

Dependências:
Definir a credencial operacional do Terminal e como será rotacionada.

Critério restante:
Migrar/remover os aliases legados e proteger leitura/finalização/checkout com a mesma
identidade de Terminal ou com o JWT do cliente, incluindo testes negativos por rota.

## [P0] Proteger GET /user/{userId}/orders e demais operações de usuário contra IDOR

Status:
INSEGURO

Tipo:
SECURITY / API / BACKEND

Problema:
`GET /user/{userId}`, `GET /user/{userId}/orders`, `PUT /user/{id}`, `POST /user/foto` e `POST /user/alterar-senha` aceitam IDs arbitrários e estão públicos.

Impacto:
Vazamento de CPF/e-mail/telefone/compras e alteração de conta/foto por outro usuário.

Arquivos relacionados:
- `infra/security/SecurityConfig.java`
- `infra/controller/UserController.java`
- `infra/service/UserService.java`
- `infra/repository/UserRepository.java`

Dependências:
Contrato de perfil próprio versus administração de usuários.

Critério de aceite:
Operações de cliente derivam o user do JWT; operações de gestor exigem role e Empresa; IDs cross-user/cross-tenant não retornam dados; testes IDOR negativos existem.

## [P0] Autenticar /terminal-socket, /payment-socket/{terminalId} e /ws

Status:
INSEGURO

Tipo:
SECURITY / INTEGRATION / BACKEND

Problema:
Handshakes aceitam qualquer origem e nenhuma credencial. Heartbeat aceita `terminalId` do payload; uma conexão pode substituir a sessão legítima ou assinar tópico de outro Terminal.

Impacto:
Falsificação de presença, interceptação de status de pagamento e reset, negação de serviço ao Terminal real.

Arquivos relacionados:
- `infra/config/WebSocketConfig.java`
- `infra/config/TerminalWebSocketConfig.java`
- `infra/websocket/TerminalWebSocketHandler.java`
- `infra/websocket/PaymentWebSocketHandler.java`
- `infra/service/PaymentSocketService.java`

Dependências:
Credencial/identidade de Terminal e política de origens.

Critério de aceite:
Handshake autentica o Terminal; identidade autenticada coincide com URL/payload/tópico; conexão cross-tenant é recusada; origem é configurável; testes de handshake, assinatura e substituição maliciosa existem.

## [P0] Impedir vínculo arbitrário em GET /checkout?id={userId}&session={sessionId}

Status:
INSEGURO

Tipo:
SECURITY / API / BACKEND

Problema:
O endpoint público associa um userId informado pelo cliente à sessão sem autenticação, ownership ou tenant.

Impacto:
Sequestro da identidade de compra e associação da compra à conta errada.

Arquivos relacionados:
- `infra/controller/CheckoutSessionController.java`
- `infra/service/CheckoutSessionService.java`
- `infra/repository/CheckoutSessionRepository.java`

Dependências:
Decidir se o fluxo legado será removido em favor de `/app-cliente/identificacao-tokens`.

Critério de aceite:
O endpoint é removido após migração ou deriva o usuário autenticado e valida Terminal/Empresa/Condomínio; tentativas com outro usuário/tenant falham; testes negativos existem.

## [P0] Proteger POST /comprovante contra envio por ID adivinhado

Status:
INSEGURO

Tipo:
SECURITY / API / INTEGRATION

Problema:
`POST /comprovante` é público e aceita `terminalId`, `pedidoId`, canal e destinatário. A combinação Order/Terminal não prova autorização do solicitante.

Impacto:
Envio externo indevido, abuso do n8n/canal e exposição de comprovante.

Arquivos relacionados:
- `infra/security/SecurityConfig.java`
- `infra/controller/comprovante/ComprovanteController.java`
- `infra/service/comprovante/ComprovanteSnapshotService.java`

Dependências:
Credencial de Terminal e manutenção do endpoint App Cliente com ownership.

Critério de aceite:
A rota exige credencial de Terminal correspondente ou é removida; o fluxo cliente permanece restrito à Order própria; testes cobrem terminal errado, tenant errado e chamada anônima.

## [P0] Limitar tentativas em POST /user/validar e normalizar CPF em toda recuperação

Status:
INSEGURO

Tipo:
SECURITY / BACKEND / TEST

Problema:
Código de seis dígitos pode ser testado sem limite durante 15 minutos. `recoveryPassword` normaliza CPF, mas validação/reset usam o valor recebido diretamente para chaves e busca.

Impacto:
Brute force de recuperação e inconsistência com CPF mascarado.

Arquivos relacionados:
- `infra/controller/UserController.java`
- `infra/service/UserService.java`
- `infra/service/CpfService.java`

Dependências:
Redis e política de bloqueio/TTL.

Critério de aceite:
CPF é normalizado em todas as etapas; código é comparado/consumido atomicamente; tentativas têm limite por CPF/IP e bloqueio; testes de brute force, replay, máscara e concorrência passam.

## P1-C concluído em 27/09/2026

Foram concluídos o snapshot financeiro autoritativo, a pipeline promoção/cupom, o
fechamento imutável da Order, a reserva/consumo/liberação do cupom, a política
executável de múltiplos `PaymentAttempt`, o estado `UNKNOWN`, a tentativa ativa única
no MySQL e a autorização do início/status/cancelamento Point pelo Terminal. A V19 e os
testes HTTP reais com MySQL/Flyway são as evidências. Detalhes em
[[payment/order-price-finalization]], [[payment/payment-attempt-policy]],
[[payment/coupon-payment-coordination]], [[payment/payment-concurrency]] e
[[payment/terminal-payment-authorization]].

O Terminal Python ainda precisa receber/provisionar `TERMINAL_INTERNAL_TOKEN` e enviar
os headers documentados antes do rollout conjunto; ele não foi alterado neste bloco.

## [P0] Estornar pontos e cashback de Order reembolsada

Status:
AUSENTE

Tipo:
BACKEND / DATABASE / TEST

Problema:
`OrderPaidEvent` credita benefício, mas transição `PROCESSED → REFUNDED` não cria movimento compensatório nem reduz saldo.

Impacto:
Cliente mantém benefício financeiro de compra devolvida e pode resgatar saldo indevido.

Arquivos relacionados:
- `infra/service/LoyaltyAccrualService.java`
- `infra/event/LoyaltyOrderListener.java`
- `infra/service/PaymentStateTransitionService.java`
- `domain/model/appclient/LoyaltyMovement.java`

Dependências:
Política para saldo já gasto e estorno parcial/futuro.

Critério de aceite:
Refund cria `ESTORNO` idempotente; não duplica em webhook/reconciliação; saldo e ledger fecham; caso de saldo já consumido tem regra explícita e testes.

## P1-A concluído em 26/09/2026

Os itens de vínculo conta/tenant, verificação de CPF por convite administrativo
pré-validado, claim tenant-aware, configuração obrigatória de
`IDENTITY_HASH_SECRET`, compilação Java 17 e integração real das 15 rotas originais
foram retirados do backlog pendente após implementação e testes. Evidências e limites
estão em [[app-cliente/account-tenant-link]], [[app-cliente/cpf-verification]],
[[app-cliente/historical-purchase-claim]], [[app-cliente/identity-hash-secret]] e
[[app-cliente/integration-tests]].

O processo operacional de emissão do convite deve validar a identidade do titular e
entregar o token por canal confiável; essa é a fronteira de confiança do mecanismo
adotado. A rotação do segredo de HMAC continua explicitamente não suportada e não deve
ser executada sem versionamento/dual-read.

## P1-B concluído em 27/09/2026

Foram retirados do backlog pendente os itens de CRUD gestor tenant-aware de
`AppClienteConfig`/fidelidade/recompensas e de constraints financeiras/tenant de
Reward, Coupon, LoyaltyAccount, LoyaltyMovement e RewardRedemption. A V18, as APIs
`/admin/app-cliente/**`, o OpenAPI e os testes HTTP + MySQL/Flyway são as evidências.
Detalhes em [[app-cliente/p1b-audit]], [[app-cliente/app-client-config]],
[[app-cliente/loyalty-config]], [[app-cliente/rewards]], [[app-cliente/coupons]] e
[[app-cliente/security]].

Isso não conclui o P0 de coordenar cupom com `PaymentAttempt`, nem os ledgers
completos, consumo de cashback ou estornos. Esses itens continuam abaixo em suas
prioridades originais.

## [P1] Fazer preflight de dados antes da V16

Status:
PARCIAL

Tipo:
DATABASE / DOCUMENTATION

Problema:
V16 normaliza todos os CPFs/e-mails. Registros distintos que colidam depois da normalização podem quebrar a migration.

Impacto:
Deploy bloqueado ou necessidade de correção manual sob indisponibilidade.

Arquivos relacionados:
- `db/migration/V16__client_registration_identity.sql`
- `domain/model/auth/User.java`

Dependências:
Snapshot/backup e política de resolução de duplicatas.

Critério de aceite:
Query de preflight detecta CPFs/e-mails inválidos/duplicados; runbook define correção; migration é validada sobre cópia representativa e banco vazio.

## [P2] Unificar visualização e envio de comprovante no mesmo snapshot

Status:
PARCIAL

Tipo:
BACKEND / API / INTEGRATION / TEST

Problema:
GET do App Cliente usa renderer Java simples e `createdAt`; envio usa snapshot FastAPI com `paidAt`, timezone e dados completos.

Impacto:
Usuário pode visualizar conteúdo diferente do enviado.

Arquivos relacionados:
- `infra/service/AppClienteService.java`
- `infra/service/comprovante/ComprovanteCompraRequest.java`
- `infra/service/comprovante/ComprovanteService.java`

Dependências:
Decidir armazenamento/regeneração do PNG.

Critério de aceite:
Visualização e envio usam o mesmo snapshot aprovado, `paidAt` e timezone; conteúdo é determinístico; testes com itens fracionados e canais passam.

## [P2] Completar ledger de pontos com tipos e expiração

Status:
PARCIAL

Tipo:
BACKEND / DATABASE / API / TEST

Problema:
Ledger usa strings `PURCHASE`/`REDEMPTION`; não modela crédito, débito, estorno, ajuste e expiração de modo validado.

Impacto:
Baixa rastreabilidade contábil e evolução arriscada.

Arquivos relacionados:
- `domain/model/appclient/LoyaltyMovement.java`
- `infra/service/LoyaltyAccrualService.java`
- `infra/service/AppClienteService.java`

Dependências:
P0 de estorno.

Critério de aceite:
Tipos persistidos são enumerados/validados; cada mutação tem idempotency key; saldo é reconstruível pelo ledger; expiração/ajuste têm ator, motivo e testes.

## [P2] Implementar consumo de cashback com ledger

Status:
AUSENTE

Tipo:
BACKEND / API / DATABASE / TEST

Problema:
Cashback só é creditado/exibido; não existe reserva, débito ou aplicação em compra.

Impacto:
Modo `CASHBACK` não entrega benefício utilizável.

Arquivos relacionados:
- `domain/model/appclient/LoyaltyAccount.java`
- `domain/model/appclient/LoyaltyMovement.java`
- `infra/service/LoyaltyAccrualService.java`

Dependências:
Regra de uso, estorno e concorrência.

Critério de aceite:
Saldo é reservado/debitado atomicamente, nunca fica indevidamente negativo, integra total da Order antes do pagamento e estorna em falha/refund com testes concorrentes.

## [P2] Completar workflow de suporte de compra

Status:
PARCIAL

Tipo:
BACKEND / API / TEST

Problema:
Só existe abertura com status `OPEN`; não há listagem do cliente/gestor, categorias controladas, resposta, atribuição ou fechamento.

Impacto:
Protocolos são persistidos mas não operáveis.

Arquivos relacionados:
- `domain/model/appclient/PurchaseSupport.java`
- `infra/repository/PurchaseSupportRepository.java`
- `infra/service/AppClienteService.java`

Dependências:
Definir processo de atendimento.

Critério de aceite:
Categorias/status são validados; cliente lista protocolos próprios; gestor lista por tenant e atualiza estado com auditoria; testes ownership/role passam.

## [P2] Configurar Termos de Uso e Política de Privacidade reais

Status:
AUSENTE

Tipo:
INTEGRATION / DOCUMENTATION / BACKEND

Problema:
URLs/versões estão vazias e nem são repassadas pelo Compose. Sem configuração, cadastro é aceito sem termos.

Impacto:
Pendente operacional/legal e ausência de trilha de aceite efetiva.

Arquivos relacionados:
- `infra/service/LegalDocumentsService.java`
- `src/main/resources/application.properties`
- `docker-compose.yml`
- `domain/model/auth/User.java`

Dependências:
Documentos publicados e versões aprovadas.

Critério de aceite:
URLs HTTPS reais e versões são fornecidas; `/public/app-config` as expõe; cadastro exige versões/aceite atuais; `acceptedAt` é persistido; atualização de versão tem política.

## [P2] Ampliar AuditLog para ações do App Cliente e autenticação sensível

Status:
PARCIAL

Tipo:
BACKEND / SECURITY / TEST

Problema:
Cadastro/vínculo/verificação, claim, resgate, emissão/aplicação de cupom, preferências e suporte não geram AuditLog.

Impacto:
Incidentes de identidade e benefício não têm trilha administrativa suficiente.

Arquivos relacionados:
- `infra/service/AuditLogService.java`
- services App Cliente/identidade/auth

Dependências:
Fluxos P1 definidos.

Critério de aceite:
Ações sensíveis geram evento tenant-aware sem CPF/token/destino em claro; rollback não deixa log órfão; testes de sanitização e ator passam.

## [P2] Validar Spring → FastAPI → n8n ponta a ponta

Status:
NÃO TESTADO

Tipo:
INTEGRATION / TEST

Problema:
Testes atuais mockam RestTemplate; FastAPI/n8n não foram inspecionados no repositório.

Impacto:
Contrato, idempotência, autenticação interna e comportamento de timeout podem divergir em produção.

Arquivos relacionados:
- `infra/service/comprovante/*`
- `docker-compose.yml`

Dependências:
Ambiente controlado, destinatário de teste e acesso ao workflow.

Critério de aceite:
Teste verifica request/response, `request_id`, correlation, imagem, destino, retry, timeout e deduplicação antes do envio; resultado fica documentado sem secrets.

## [P2] Declarar autenticação Bearer e erros no OpenAPI

Status:
PARCIAL

Tipo:
API / DOCUMENTATION

Problema:
O P1-B adicionou o esquema Bearer e documentou integralmente as novas rotas
`/admin/app-cliente/**`, mas as rotas legadas ainda não possuem documentação
consistente de roles, credencial interna do Terminal e erros.

Impacto:
Consumidores podem integrar contratos de segurança incorretos.

Arquivos relacionados:
- `pom.xml`
- controllers
- `infra/config/OpenApiConfig.java`
- controllers legados ainda sem anotações completas

Dependências:
Fronteira P0 estabilizada.

Critério de aceite:
OpenAPI marca rotas públicas, cliente, gestor e Terminal; inclui Bearer/credencial interna; respostas 400/401/403/404/409/422/429 são documentadas.

## [P3] Endurecer JWT e resposta a token inválido

Status:
PARCIAL

Tipo:
SECURITY / BACKEND

Problema:
JWT dura 200 horas, não possui issuer/audience/refresh/revogação; token malformado pode virar 500.

Impacto:
Janela longa de abuso e contrato de autenticação inconsistente.

Arquivos relacionados:
- `infra/service/TokenService.java`
- `infra/security/SecurityFilter.java`
- `infra/security/SecurityConfig.java`

Dependências:
Política de sessão dos clientes.

Critério de aceite:
Token inválido/expirado retorna 401 estruturado; TTL e claims são configuráveis; logout/revogação/refresh têm decisão documentada e testes.

## [P3] Paginar catálogo App Cliente no banco

Status:
PARCIAL

Tipo:
BACKEND / DATABASE / TEST

Problema:
Service carrega todo o catálogo e só depois filtra/recorta em memória.

Impacto:
Latência e memória crescem com o catálogo.

Arquivos relacionados:
- `infra/service/AppClienteService.java`
- `infra/repository/EstoqueCondominioRepository.java`

Dependências:
Contrato de busca/categoria/ordenação.

Critério de aceite:
Query paginada filtra Condomínio, estoque/produto ativos, busca e categoria; total é calculado no banco; índices suportam o plano; preço promocional permanece consistente.

## [P3] Remover endpoints de teste Mercado Pago da aplicação produtiva

Status:
INSEGURO

Tipo:
SECURITY / BACKEND / DOCUMENTATION

Problema:
Controllers de teste fazem chamadas reais e ficam habilitados por padrão fora do profile `prod`.

Impacto:
Ambiente mal configurado pode expor operações externas públicas.

Arquivos relacionados:
- `infra/controller/TestarWebhookMPController.java`
- `src/main/resources/application.properties`
- `docker-compose.yml`

Dependências:
Alternativa de teste isolada.

Critério de aceite:
Código não é registrado por padrão e nunca em produção; habilitação exige profile explícito e autenticação; teste confirma ausência das rotas.

## [P3] Confirmar política de catálogo para saldo zero ou negativo

Status:
PARCIAL

Tipo:
BACKEND / DOCUMENTATION

Problema:
Produto aparece no catálogo quando `EstoqueCondominio.ativo=true`, independentemente da quantidade.

Impacto:
Pode ser regra deliberada de estoque permissivo ou permitir compra sem saldo; a decisão precisa ser explícita para o App Cliente.

Arquivos relacionados:
- `infra/repository/EstoqueCondominioRepository.java`
- `infra/service/PricingService.java`
- `infra/service/AppClienteService.java`

Dependências:
Decisão de produto/operação.

Critério de aceite:
Política é aprovada e testada; se saldo positivo for requisito, catálogo/checkout aplicam a mesma regra atômica; se não for, contrato exibe/explica a disponibilidade.
