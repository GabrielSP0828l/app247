# IDENTITY_HASH_SECRET

## Configuração

`CustomerIdentityHashService` lê a propriedade obrigatória:

`app247.identity.hash-secret=${IDENTITY_HASH_SECRET}`

O valor precisa ter pelo menos 32 caracteres. Ausência, placeholder não resolvido ou
valor curto interrompe o startup com mensagem de configuração clara. O valor não é
impresso em log, resposta HTTP ou documentação.

O `docker-compose.yml` repassa a variável ao backend com validação obrigatória do
Compose. `.env.example` contém apenas o nome vazio e instruções; o segredo real deve
vir do secret manager ou ambiente de deploy e nunca ser versionado. Todas as réplicas
que compartilham o banco precisam receber exatamente o mesmo valor.

Exemplo de geração operacional (não copie o resultado para o Git):

```bash
openssl rand -base64 48
```

## Uso

O segredo é a chave de HMAC-SHA256 para CPF normalizado. O hash é chave de lookup de
`CustomerIdentity` e de convites de vínculo; CPF em claro não é gravado nessas
tabelas.

## Rotação

**ROTATION NOT YET SUPPORTED.** Trocar o segredo imediatamente muda todos os hashes e
torna identidades e convites antigos impossíveis de localizar. Uma rotação futura
precisa de versão da chave e janela de dual-read/rehash testada antes da troca. Até
isso existir, o segredo deve ser estável, protegido, com backup e acesso restrito.
