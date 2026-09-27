# Testes de integração do App Cliente

## Estratégia

`AppClienteEndpointsIntegrationTest` usa `@SpringBootTest` e MockMvc através da cadeia
real HTTP → Spring Security/JWT → `AppClienteService` → repositories JPA → banco de
teste. O service principal não é mockado. Apenas Redis e a integração externa de
comprovante são substituídos nos cenários que não testam esses serviços externos.

`TenantLinkClaimIntegrationTest` percorre HTTP, security, service e JPA reais para o
convite, vínculo, CPF verificado, claim, novo JWT e histórico. Ele cobre também CPF
não verificado, convite de CPF divergente, Condomínio cross-company, identidade com o
mesmo CPF em outra Empresa, retry e ausência de fidelidade retroativa.

`AdminAppClienteManagementIntegrationTest` percorre HTTP, Spring Security, services e
repositories reais sobre MySQL/Flyway. Ele cobre os papéis administrativos, isolamento
entre Empresas, os três modos de fidelidade, CRUD/validações de recompensa, flags do
bootstrap, resgate cross-tenant e imutabilidade do snapshot do cupom.

`DatabaseBaselineMySqlTest` usa MySQL 8.4 Testcontainers a partir de schema vazio,
executa as 19 migrations Flyway e inicia Hibernate com `ddl-auto=validate`. Além das
constraints do P1-A, a V18 é exercitada para unicidade da configuração por Empresa,
checks financeiros e FKs compostas de fidelidade/cupom.

## Matriz das 15 rotas originais

| Endpoint | Auth | Tenant | Ownership | Integração real |
|---|---|---|---|---|
| `GET /bootstrap` | JWT USER e anônimo negativo | User → Empresa → Condomínio; query IDs ignorados | próprio User | sim |
| `GET /catalogo` | JWT USER e anônimo negativo | Empresa + Condomínio em estoque/produto | n/a | sim; promoção, inativos e cross-tenant |
| `GET /compras` | JWT USER e anônimo negativo | Empresa + Condomínio | user ou identidade claimed | sim |
| `GET /compras/{id}` | JWT USER e anônimo negativo | Empresa + Condomínio | outro user/tenant retorna 404 | sim |
| `GET /compras/{id}/comprovante` | JWT USER e anônimo negativo | igual ao detalhe | Order própria | sim; renderer real |
| `POST /compras/{id}/comprovante/envios` | JWT USER e anônimo negativo | igual ao detalhe | Order própria | sim; integração externa mockada |
| `POST /identificacao-tokens` | JWT USER e anônimo negativo | User vinculado | próprio User | sim; Redis mockado |
| `GET /fidelidade` | JWT USER e anônimo negativo | Empresa | própria conta | sim |
| `GET /recompensas` | JWT USER e anônimo negativo | Empresa/Condomínio | saldo próprio | sim |
| `POST /recompensas/{id}/resgates` | JWT USER e anônimo negativo | Empresa/Condomínio | conta própria | sim |
| `GET /cupons` | JWT USER e anônimo negativo | Empresa | cupons próprios | sim |
| `POST /cupons/{id}/tokens` | JWT USER e anônimo negativo | Empresa | cupom próprio | sim; Redis mockado |
| `GET /preferencias` | JWT USER e anônimo negativo | Empresa | preferência própria | sim |
| `PUT /preferencias` | JWT USER e anônimo negativo | Empresa | preferência própria | sim |
| `POST /compras/{id}/suporte` | JWT USER e anônimo negativo | Empresa + Condomínio | Order própria | sim |

As duas rotas novas de vínculo também possuem integração real: emissão exige
`ADMIN/GERENTE`, consumo exige `USER`, e ambas têm casos negativos sem JWT/papel
incorreto.

## Cobertura adicionada no P1-B

- 7 testes HTTP de integração em `AdminAppClienteManagementIntegrationTest`, cada um
  cobrindo conjuntos de cenários positivos e negativos sem mock do service principal;
- 1 teste MySQL/Flyway adicional em `DatabaseBaselineMySqlTest` para as constraints da
  V18;
- fixtures da fachada App Cliente ajustadas para validar a configuração explícita por
  Empresa e os novos tipos de desconto;
- regressão direta de 39 testes para App Cliente, QR do terminal e ausência de
  fidelidade retroativa, além da suíte completa.

## Resultado verificado em 27/09/2026

Com OpenJDK 17.0.20.1 e Maven Wrapper 3.9.14:

`IDENTITY_HASH_SECRET=<valor-de-teste> ./mvnw test`

Resultado: **329 testes, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. Os 55
testes diretamente ligados ao P1-A continuam ativos: 35 da fachada App Cliente, 4 de
vínculo/claim, 8 de cadastro, 2 unitários do HMAC e 6 de MySQL/Flyway/constraints. O
P1-B acrescentou 8 testes de integração/constraints e ampliou os cenários cobertos
pelas fixtures existentes.
