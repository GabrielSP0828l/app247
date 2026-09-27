# Vínculo da conta ao tenant

## Modelo executável

O vínculo autoritativo do App Cliente é:

`User.empresa_id → Empresa` e `User.condominio_id → Condominio → Empresa`.

Uma conta possui, no modelo atual, no máximo uma Empresa e um Condomínio ativos. Não
há tabela associativa para múltiplos condomínios e o backend não escolhe um entre
vários. Contas criadas por `POST /auth/register` começam com os dois campos nulos.

A FK composta já existente
`users(condominio_id, empresa_id) → condominio(id, empresa_id)` impede que uma conta
aponte para Condomínio de outra Empresa. A nulabilidade de `empresa_id` foi
introduzida na V16 para permitir o estado pré-vínculo.

## Convite autorizado

Não havia convite, morador pré-cadastrado, código de Condomínio ou associação
administrativa reutilizável. A solução mínima é um convite emitido pelo backend:

1. um `ADMIN` ou `GERENTE` autenticado chama
   `POST /admin/app-cliente/tenant-invitations` com CPF e `condominioId`;
2. a Empresa é derivada exclusivamente do JWT/`EmpresaContext`;
3. o backend aceita somente Condomínio ativo da Empresa autenticada;
4. `tenant_link_invitation` guarda Empresa, Condomínio, HMAC do CPF, hash SHA-256 do
   token aleatório de 256 bits, emissor, expiração e eventual consumidor;
5. o token opaco é entregue ao titular por canal administrativo confiável;
6. a conta `USER` autenticada chama `POST /app-cliente/tenant-link` apenas com o token;
7. sob locks pessimistas, o backend confere expiração, uso, tenant e CPF, grava o
   vínculo e devolve um JWT novo contendo `empresaId`.

O frontend nunca envia `empresaId` ou `condominioId` no consumo. Convite inexistente,
expirado, já usado por outra conta ou com CPF divergente retorna o mesmo erro opaco
`TENANT_INVITATION_INVALID`.

O token antigo, emitido quando a conta ainda não tinha Empresa, deixa de autenticar
após o vínculo porque o filtro compara os claims do JWT com o estado atual do banco.

## Bootstrap e contexto

`GET /app-cliente/bootstrap`, catálogo e histórico recarregam o `User` autenticado e
exigem que `User.empresa`, `EmpresaContext` e `User.condominio.empresa` sejam
coerentes. Parâmetros arbitrários como `?empresaId=` ou `?condominioId=` não participam
do contrato nem da resolução do tenant.

Conta ainda não vinculada recebe `409 TENANT_LINK_REQUIRED`; Condomínio ausente ou
inativo recebe `409 CONDOMINIUM_REQUIRED`. Não há fallback para o primeiro ou último
Condomínio do banco.

## Constraints e concorrência

- token de convite é único e armazenado somente como hash;
- Empresa e Condomínio do convite têm FK composta tenant-aware;
- emissor e consumidor têm FK para `users`;
- `used_at` e `used_by_user_id` devem estar ambos nulos ou ambos preenchidos;
- usuário e convite são carregados com `PESSIMISTIC_WRITE` durante o consumo;
- retry do mesmo usuário e token já consumido é idempotente;
- uma conta já vinculada não pode trocar tenant por outro convite.

A migration e suas constraints são validadas no MySQL 8.4 por Testcontainers.
