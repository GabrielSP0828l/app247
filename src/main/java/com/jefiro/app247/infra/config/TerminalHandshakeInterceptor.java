package com.jefiro.app247.infra.config;

import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;

/** Autentica o upgrade WebSocket antes de registrar qualquer sessao de Terminal. */
@Component
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {
    @Autowired private TerminalRequestAuthorizationService authorization;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String credential = request.getHeaders().getFirst("X-Terminal-Token");
        String path = request.getURI().getPath();
        AuthenticatedTerminal identity;
        if (path.startsWith("/payment-socket/")) {
            String terminalId = path.substring(path.lastIndexOf('/') + 1);
            identity = authorization.authorizeChannel(credential, terminalId);
        } else {
            identity = authorization.authenticateChannel(credential);
        }
        attributes.put(AuthenticatedTerminal.class.getName(), identity);
        attributes.put("terminalId", identity.terminalId());
        attributes.put("empresaId", identity.empresaId());
        attributes.put("condominioId", identity.condominioId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // Sem estado transitorio: a autorizacao termina antes do upgrade.
    }
}
