package com.jefiro.app247.infra.config;

import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Autoriza o STOMP exclusivamente pela identidade estabelecida no handshake HTTP. */
@Component
public class TerminalStompAuthorizationInterceptor implements ChannelInterceptor {
    @Autowired TerminalRequestAuthorizationService authorization;
    private final Map<String, AuthenticatedTerminal> sessions = new ConcurrentHashMap<>();
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null || command == StompCommand.UNSUBSCRIBE) {
            return message;
        }
        if (command == StompCommand.DISCONNECT) {
            if (accessor.getSessionId() != null) sessions.remove(accessor.getSessionId());
            return message;
        }
        if (command == StompCommand.ERROR) return message;
        if (command == StompCommand.MESSAGE || command == StompCommand.CONNECTED
                || command == StompCommand.RECEIPT) {
            AuthenticatedTerminal terminal = sessions.get(accessor.getSessionId());
            authorization.revalidate(terminal);
            return message;
        }
        AuthenticatedTerminal terminal = authenticated(accessor.getUser());
        authorization.revalidate(terminal);
        if (command == StompCommand.CONNECT) {
            if (accessor.getSessionId() != null) sessions.put(accessor.getSessionId(), terminal);
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            String expected = "/topic/payment/" + terminal.terminalId();
            if (!expected.equals(accessor.getDestination())) throw denied();
            return message;
        }
        // Não existem handlers @MessageMapping: o canal STOMP é somente server-push.
        if (command == StompCommand.SEND) throw denied();
        return message;
    }

    private AuthenticatedTerminal authenticated(Principal principal) {
        if (principal instanceof AuthenticatedTerminal terminal) return terminal;
        throw denied();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("STOMP não autorizado para esta identidade de Terminal");
    }
}
