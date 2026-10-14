# CLAUDE.md — Backend App 24/7

As regras gerais do projeto estão em `AGENTS.md` (fonte compartilhada com outros agentes)
e são importadas abaixo. Este arquivo só acrescenta instruções específicas do Claude Code.

@AGENTS.md

## Escopo

- Este repositório contém **exclusivamente o backend** Spring Boot (Java 17).
- Apps Flutter, Terminal Python, FastAPI de comprovante e n8n ficam em outros repositórios:
  consulte-os apenas para entender contratos; não os altere a partir daqui.

## Contexto

- Leia `docs/CLAUDE_HANDOFF.md` no início de cada sessão: estado real, comandos,
  riscos e pendências.
- Antes de alterar uma funcionalidade, leia o documento específico em `24por7_contexto/`
  (índice em `24por7_contexto/00-index.md`). Vários documentos antigos estão desatualizados;
  o handoff lista os conhecidos. Em divergência, o código executável vence.

## Regras de trabalho

- Preserve alterações locais: o working tree já acumulou trabalho grande não commitado
  (inclusive migrations não rastreadas). Confira `git status` ao começar. Nunca use `git checkout -- .`, `git reset --hard`,
  `git clean` ou `git stash` sem pedido explícito.
- Não faça commit nem push sem pedido explícito. Push em `master` roda `./mvnw test` e, se
  passar, faz build da imagem Docker e deploy automático na EC2 (`.github/workflows/docker-publish.yml`).
- Não altere contratos de API (rotas, DTOs, códigos de erro, eventos WebSocket) sem verificar
  os consumidores (Terminal Python, Flutter admin, App Cliente) e documentar o impacto.
- Migrations Flyway em `src/main/resources/db/migration` são imutáveis depois de aplicadas:
  crie sempre uma nova `V<n>__*.sql`; nunca edite uma existente sem avaliar bancos implantados.
  Mudança em entidade JPA exige migration correspondente (`ddl-auto=validate`).
- Preserve o isolamento multi-tenant (`Empresa -> Condomínio -> Terminal`), consultando por
  recurso + empresa e nunca confiando em IDs vindos do cliente.
- Nunca exponha nem versione secrets (`.env`, `.env.*` são ignorados; só `.env.example` entra).

## Validação

- Compilar/testar: `./mvnw test` (Docker necessário para os testes Testcontainers MySQL;
  sem Docker eles são ignorados, não aprovados).
- Execute os testes pertinentes e relate o resultado real (comando, contagem, falhas).
- Não declare tarefa concluída sem evidência (teste, execução ou inspeção citada).
- Após mudanças em arquitetura, banco, autenticação, Mercado Pago, WebSocket, fluxo do
  Terminal ou contratos, atualize `24por7_contexto/` e, se mudar o estado geral,
  `docs/CLAUDE_HANDOFF.md`.
