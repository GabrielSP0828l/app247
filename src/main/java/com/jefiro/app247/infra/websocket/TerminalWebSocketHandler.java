package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.infra.service.TerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.CloseStatus;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    TerminalService service;
    @Autowired
    TerminalRequestAuthorizationService authorization;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        TerminalStatusDTO dto =
                objectMapper.readValue(
                        message.getPayload(),
                        TerminalStatusDTO.class
        );
        if (!(session.getPrincipal() instanceof AuthenticatedTerminal identity)
                || !identity.terminalId().equals(dto.terminalId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            throw new AccessDeniedException("Heartbeat não pertence ao Terminal autenticado");
        }
        authorization.revalidate(identity);
        var terminal = service.updateStatus(dto);
        var acknowledgement = new LinkedHashMap<String, Object>();
        acknowledgement.put("type", "HEARTBEAT_ACK");
        acknowledgement.put("terminalId", terminal.getIdTerminal());
        acknowledgement.put("status", terminal.getStatus().name());
        acknowledgement.put("lastPing", terminal.getLastPing().toString());
        acknowledgement.put("lifecycleState", terminal.getLifecycleState().name());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(acknowledgement)));
    }
}
