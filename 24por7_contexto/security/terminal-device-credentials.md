# Credencial individual do dispositivo

Cada Terminal pode possuir uma credencial `tdc_...` gerada com `SecureRandom` e 32
bytes de entropia. O valor bruto aparece somente na resposta de provisionamento ou
rotação. O banco armazena apenas SHA-256 hexadecimal; hashing simples é adequado aqui
porque a entrada é aleatória, longa e não é uma senha humana.

A V20 adiciona em `terminal`:

- `device_credential_hash CHAR(64)`, único;
- `device_credential_version INT`, com check `>= 1` quando existe hash;
- `device_credential_created_at`;
- `device_credential_revoked_at`;
- índice de estado por revogação/atividade.

O lookup pelo hash deriva Terminal, Condomínio e Empresa. O Principal não contém a
credencial bruta. A versão permite invalidar imediatamente sessões abertas após
rotação; revogação e estado operacional também são revalidados durante heartbeat e
entrega por socket.

## Administração tenant-aware

- `POST /terminais/{id}/credential`: provisiona quando não existe credencial ativa;
- `POST /terminais/{id}/credential/rotate`: troca e retorna a nova uma vez;
- `DELETE /terminais/{id}/credential`: revoga somente aquele equipamento.

As rotas seguem a proteção administrativa `ADMIN`/`GERENTE` existente e usam
`EmpresaContext`; Terminal de outra Empresa é tratado como inexistente. Lock
pessimista serializa provisionamento/rotação concorrentes.
