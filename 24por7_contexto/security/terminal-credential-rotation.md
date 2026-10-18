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

## Migração do segredo global (removida)

A migração por `TERMINAL_INTERNAL_TOKEN` foi removida em 18/10/2026. Terminal sem
credencial recebe uma pelo fluxo administrativo (`POST /terminais/{id}/credential`) e
instalação local. Alertas de uso da mesma credencial em origens diferentes ficam em
`audit_log` (`TERMINAL_CREDENTIAL_ORIGIN_CHANGED`); ver [[terminal-authentication]].
