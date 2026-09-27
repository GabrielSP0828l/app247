package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Autentica o canal interno e vincula toda operação ao Terminal persistido. */
@Service
public class TerminalRequestAuthorizationService {
    @Autowired private TerminalRepository terminals;
    @Value("${app.terminal.internal-token:}") private String internalToken;

    @Transactional(readOnly = true)
    public Terminal authorize(String credential, String terminalId) {
        authenticate(credential);
        if (terminalId == null || terminalId.isBlank()) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "TERMINAL_ID_REQUIRED",
                    "Identificador do Terminal é obrigatório");
        }
        Terminal terminal = terminals.findByIdWithTenant(terminalId).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND",
                        "Terminal não encontrado"));
        if (!Boolean.TRUE.equals(terminal.getAtivo())
                || terminal.getLifecycleState() != TerminalLifecycleState.ACTIVE
                || terminal.getCondominio() == null
                || !Boolean.TRUE.equals(terminal.getCondominio().getAtivo())
                || terminal.getCondominio().getEmpresa() == null
                || !Boolean.TRUE.equals(terminal.getCondominio().getEmpresa().getAtivo())
                || terminal.getCondominio().getEmpresa().isDefinitivamenteEncerrada()) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "TERMINAL_NOT_OPERATIONAL",
                    "Terminal não está autorizado a operar");
        }
        return terminal;
    }

    public void requireCartOwnership(Terminal terminal, Carrinho cart) {
        if (terminal == null || cart == null || cart.getTerminal() == null
                || !terminal.getIdTerminal().equals(cart.getTerminal().getIdTerminal())
                || cart.getEmpresa() == null || cart.getCondominio() == null
                || !terminal.getCondominio().getEmpresa().getId().equals(cart.getEmpresa().getId())
                || !terminal.getCondominio().getIdCondominio().equals(
                        cart.getCondominio().getIdCondominio())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "CART_NOT_OWNED",
                    "Carrinho não pertence ao Terminal autenticado");
        }
    }

    public void requireOrderOwnership(Terminal terminal, Order order) {
        if (order == null || order.getTerminal() == null
                || !terminal.getIdTerminal().equals(order.getTerminal().getIdTerminal())
                || order.getEmpresa() == null || order.getCondominio() == null
                || !terminal.getCondominio().getEmpresa().getId().equals(order.getEmpresa().getId())
                || !terminal.getCondominio().getIdCondominio().equals(
                        order.getCondominio().getIdCondominio())) {
            throw new ApiBusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_OWNED",
                    "Order não pertence ao Terminal autenticado");
        }
    }

    private void authenticate(String credential) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new ApiBusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TERMINAL_AUTH_NOT_CONFIGURED",
                    "Autenticação interna do Terminal não configurada");
        }
        if (credential == null || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                credential.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, "TERMINAL_AUTH_INVALID",
                    "Credencial do Terminal inválida");
        }
    }
}
