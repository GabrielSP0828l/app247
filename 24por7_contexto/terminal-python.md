# Terminal Python — distribuição e fronteira com o backend

Voltar para [[00-index]]. Contrato executável em [[terminal-integration]] e
segurança em [[security/terminal-authentication]].

O Terminal Python é distribuído como PyInstaller `onedir`; sua release
imutável fica em `/opt/app247/releases/<versão>`, o symlink ativo em
`/opt/app247/current`, configuração em `/etc/app247` e estado operacional em
`/var/lib/app247`. O empacotamento não altera DTOs, endpoints HTTP/WebSocket nem
a autoridade do backend sobre Terminal, catálogo, Order, pagamento e estoque.

O artefato oficial usa nome de arquitetura Debian (`armhf`, `arm64` ou `amd64`)
confirmado contra o ELF. O manifesto schema 1 contém versão, arquitetura, nome,
SHA-256, tamanho, algoritmo Ed25519 e assinatura. A chave privada permanece no
cofre de release; a chave pública é instalada como trust anchor externa em
`/etc/app247/update-signing-public-key.pem`.

O instalador standalone preserva `/etc/app247` e `/var/lib/app247`, prepara uma
release sem sobrescrever conteúdo divergente e só troca `current` na etapa
explícita `--activate`. O diagnóstico de ativação continua validando a conexão
real com `GET /terminal/health`; somente o diagnóstico isolado do build dispensa
rede e credenciais de produção.
