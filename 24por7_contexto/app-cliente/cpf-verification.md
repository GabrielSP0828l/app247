# Verificação de CPF do App Cliente

O backend mantém separados três conceitos:

1. **CPF sintaticamente válido** — normalização e dígitos verificadores por
   `CpfService`;
2. **CPF armazenado** — `users.cpf`, único e com 11 dígitos;
3. **CPF verificado** — `users.cpf_verified_at` preenchido somente após uma prova de
   vínculo autorizada.

O cadastro valida sintaxe, mas mantém `cpf_verified_at = NULL`. Informar CPF no
Terminal cria ou localiza `CustomerIdentity` por HMAC e não cria conta, autenticação,
verificação ou benefício.

## Mecanismo real adotado

O mecanismo disponível nesta etapa é o convite administrativo pré-validado e preso
ao CPF. Um gestor autenticado no tenant emite o convite para um CPF e Condomínio
específicos e deve entregá-lo ao titular por um canal confiável. O consumo exige ao
mesmo tempo:

- conta `USER` autenticada;
- token opaco aleatório ainda válido e não usado;
- HMAC do CPF da conta igual ao HMAC preso ao convite;
- Empresa e Condomínio ativos e coerentes com o emissor.

Somente o consumo bem-sucedido grava `cpf_verified_at`. Não existe boolean de
verificação aceito do cliente, OTP impresso em log ou promoção automática de CPF
sintaticamente válido para `VERIFIED`.

O trust boundary operacional é explícito: antes de emitir e entregar o convite, a
administração precisa validar a identidade do titular. Se esse processo externo não
for cumprido, o backend não consegue transformar o convite em prova confiável.

## Estados observáveis

- `UNVERIFIED`: `cpf_verified_at` nulo; histórico por `CustomerIdentity` não é
  reivindicado nem exibido;
- `VERIFIED`: timestamp preenchido pelo consumo do convite; o claim pode executar;
- o CPF em texto não é usado para consultar compras históricas: a chave é HMAC-SHA256
  estável por Empresa.
