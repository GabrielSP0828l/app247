package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.terminal.Terminal;

/**
 * Resposta pública de {@code GET /terminal/serial/{serial}}: o Terminal ainda não tem
 * credencial nesse momento, então só expõe o necessário para ativar. Nome, código,
 * status e Condomínio ficam restritos às rotas autenticadas.
 */
public record TerminalActivationResponse(
        String terminalId,
        String serialNumber,
        Boolean ativo,
        Boolean activated
) {
    public TerminalActivationResponse(Terminal terminal) {
        this(terminal.getIdTerminal(), terminal.getSerialNumber(), terminal.getAtivo()
                , terminal.getLifecycleState() == com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState.ACTIVE
                        && Boolean.TRUE.equals(terminal.getAtivo())
                        && Boolean.TRUE.equals(terminal.getCondominio().getEmpresa().getAtivo())
                        && !terminal.getCondominio().getEmpresa().isDefinitivamenteEncerrada());
    }

}
