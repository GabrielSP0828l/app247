# Revogação, rotação e migração

## Revogação

Revogar A preenche `device_credential_revoked_at`. A credencial de A passa a retornar
401 em HTTP e em novos handshakes; sessões abertas deixam de enviar/receber operação
na próxima validação. Nenhum hash ou versão de B é alterado.

## Rotação

A rotação ocorre sob transação e lock do Terminal: gera A2, substitui hash, incrementa
a versão, limpa revogação, faz `saveAndFlush` e só então devolve A2. A1 deixa de ser
encontrada e sessões com a versão anterior são rejeitadas. Não existe janela com duas
credenciais válidas.

## Migração do segredo global

O modo legado nasce desligado. Quando explicitamente ligado, um Terminal ainda sem
hash usa `TERMINAL_INTERNAL_TOKEN` somente para obter sua primeira credencial em
`POST /terminal/{id}/credential/migrate`. Repetição retorna conflito; o token global
não é fallback para operação diária. Depois de migrar a frota, desligar
`TERMINAL_LEGACY_AUTH_ENABLED` e rotacionar/remover o segredo global do ambiente.
