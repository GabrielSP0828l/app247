# Segurança da configuração do App Cliente

## Autorização HTTP

`/admin/app-cliente/**` é coberto pelo matcher `/admin/**` e exige `ROLE_ADMIN` ou
`ROLE_GERENTE`. Testes reais confirmam `403` para `USER`, `MORADOR`, `PORTARIA` e
requisição anônima. `/app-cliente/**` continua exigindo `ROLE_USER`.

As rotas administrativas não recebem `empresaId`. O filtro JWT valida usuário e
Empresa persistidos, preenche `EmpresaContext`, e services/repositories usam esse ID
em toda leitura e escrita. IDs de recompensa de outra Empresa retornam o mesmo 404 de
um ID inexistente.

## Defesa em profundidade

A autorização de service é acompanhada por constraints MySQL:

- `app_cliente_config.empresa_id` é PK/FK;
- Reward/Condomínio, Coupon/User/Reward/Order, Redemption e LoyaltyMovement usam
  relações tenant-aware;
- checks financeiros e enums impedem estados inválidos por escritores alternativos;
- chaves únicas impedem configuração duplicada, dois resgates para o mesmo cupom e
  dois cupons reservados na mesma Order.

Violações de entrada conhecidas são transformadas em erros de domínio/validação. O
handler não retorna SQL, Hibernate nem detalhes de `DataIntegrityViolationException`.

## OpenAPI

O OpenAPI declara o esquema HTTP Bearer `bearerAuth`. Todas as rotas novas de gestão
possuem tag, requisito JWT, papéis na descrição, request/response tipados e respostas
400/401/403; operações por ID também documentam 404 opaco.

A documentação global das rotas legadas e da credencial interna do Terminal ainda é
parcial e permanece como P2; o P1-B documenta integralmente apenas suas rotas novas.
