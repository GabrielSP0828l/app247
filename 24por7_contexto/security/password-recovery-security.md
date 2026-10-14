# Segurança da recuperação de senha

## Contrato

1. `POST /user/recuperar` normaliza o CPF e sempre responde `202` com a mesma
   mensagem, exista ou não conta.
2. Para conta existente, é gerado código aleatório de seis dígitos. Redis armazena
   somente HMAC do código, com TTL configurável.
3. `POST /user/validar` compara e consome o código atomicamente por Lua. Em sucesso,
   gera token aleatório de 32 bytes; somente o HMAC do token é armazenado.
4. `POST /user/redefinir-senha` consome atomicamente o token e o ponteiro ativo,
   bloqueia o usuário na transação e grava a nova senha com BCrypt.

Código/token inválido, expirado ou reutilizado retorna `PASSWORD_RESET_INVALID` sem
revelar a causa. Um novo token invalida o anterior. Duas redefinições concorrentes
com o mesmo token permitem no máximo uma alteração.

## Limites

Os contadores combinam hash do CPF normalizado e hash da origem, sem CPF/IP puro nas
chaves. As propriedades são:

- `app247.password-recovery.window` / `PASSWORD_RECOVERY_WINDOW`;
- `request-limit`, `validate-limit`, `reset-limit` e variáveis equivalentes;
- `code-ttl` e `token-ttl`.

Os defaults são cinco operações por janela de quinze minutos e TTL de quinze
minutos. Excesso retorna 429 `PASSWORD_RESET_RATE_LIMITED`. Redis é a coordenação
distribuída; o fallback local mantém limite conservador durante indisponibilidade.
Código, token, CPF completo e segredo HMAC não são registrados em log.
