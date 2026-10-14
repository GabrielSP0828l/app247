# Isolamento cross-terminal e cross-tenant

A fronteira de autorização é o equipamento, não apenas a Empresa. A credencial resolve
`Terminal -> Condomínio -> Empresa` e IDs externos precisam coincidir com os três.

São rejeitados com 403 tanto `A(E1) -> B(E2)` quanto `A1(E1) -> A2(E1)` em sync,
catálogo, telemetria, lifecycle, payment socket e destinations STOMP. Heartbeat A com
payload B fecha antes de persistir `lastPing/status`. Administração de credenciais usa
o tenant do JWT e devolve 404 para Terminal de outra Empresa.

Testes de integração atravessam o serviço real e repository JPA; testes HTTP cobrem
ausência, inválida, válida, outro equipamento, outro tenant e Terminal desativado.
