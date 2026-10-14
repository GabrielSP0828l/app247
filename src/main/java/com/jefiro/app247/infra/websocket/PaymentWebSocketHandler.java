package com.jefiro.app247.infra.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.event.TerminalFactoryResetRequiredEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.security.access.AccessDeniedException;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import com.jefiro.app247.infra.exception.ApiBusinessException;

@Component
public class PaymentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebSocketHandler.class);
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TerminalRequestAuthorizationService authorization;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (!(session.getPrincipal() instanceof AuthenticatedTerminal identity)) {
            throw new AccessDeniedException("Socket de pagamento sem identidade de Terminal");
        }
        String terminalId = identity.terminalId();

        sessions.put(terminalId, session);

        log.info("[PAYMENT-WS] socket nativo conectado terminalId={} activeSessions={}",
                terminalId, sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        sessions.values().remove(session);
        log.info("[PAYMENT-WS] socket nativo desconectado status={} activeSessions={}",
                status, sessions.size());
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendToTerminal(PaymentEvent event) {
        if (sendToTerminal(event.getTerminalId(), event)) {
            log.info("Resultado de pagamento enviado ao terminal: terminalId={}, orderId={}, status={}",
                    event.getTerminalId(), event.getOrderId(), event.getStatus());
        } else {
            log.info("[PAYMENT-WS] terminal sem socket nativo; status permanece consultável terminalId={} orderId={} status={} activeSessions={}",
                    event.getTerminalId(), event.getOrderId(), event.getStatus(), sessions.size());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendFactoryResetToTerminal(TerminalFactoryResetRequiredEvent event) {
        if (!sendToTerminal(event.terminalId(), event, false)) {
            log.info("[TERMINAL-RESET] terminal offline; comando permanece no bootstrap terminalId={}",
                    event.terminalId());
        }
    }

    public boolean sendToTerminal(String terminalId, Object payload) {
        return sendToTerminal(terminalId, payload, true);
    }

    private boolean sendToTerminal(String terminalId, Object payload, boolean requireOperational) {
        WebSocketSession session = sessions.get(terminalId);
        if (session == null || !session.isOpen()) return false;
        try {
            if (!(session.getPrincipal() instanceof AuthenticatedTerminal identity)) return false;
            if (requireOperational) authorization.revalidate(identity);
            else authorization.revalidateLifecycle(identity);
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException | ApiBusinessException e) {
            sessions.remove(terminalId, session);
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
            log.warn("Falha ao enviar mensagem ao terminal: terminalId={}", terminalId, e);
            return false;
        }
    }
}
