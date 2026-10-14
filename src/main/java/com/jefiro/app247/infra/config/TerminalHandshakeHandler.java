package com.jefiro.app247.infra.config;

import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/** Propaga a identidade autenticada pelo interceptor como Principal da sessão. */
@Component
public class TerminalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object identity = attributes.get(AuthenticatedTerminal.class.getName());
        return identity instanceof AuthenticatedTerminal terminal ? terminal : null;
    }
}
