package com.jefiro.app247.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jefiro.app247.domain.model.dto.TerminalStatusDTO;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.service.TerminalService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;

class TerminalWebSocketHandlerHeartbeatTest {
    @Test
    void respondeAckSomenteDepoisDoServiceRetornarLastPing() throws Exception {
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TerminalService service = mock(TerminalService.class);
        inject(handler, "objectMapper", mapper);
        inject(handler, "service", service);
        TerminalRequestAuthorizationService authorization = mock(TerminalRequestAuthorizationService.class);
        inject(handler, "authorization", authorization);

        Terminal terminal = new Terminal();
        terminal.setIdTerminal("terminal-a");
        terminal.setStatus(TerminalStatus.ONLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        terminal.setLastPing(Instant.parse("2026-08-24T15:00:00Z"));
        when(service.updateStatus(any(TerminalStatusDTO.class))).thenReturn(terminal);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getPrincipal()).thenReturn(
                new AuthenticatedTerminal("terminal-a", "empresa-a", "condominio-a", 1));
        when(authorization.revalidate(any())).thenAnswer(call -> call.getArgument(0));
        handler.handleTextMessage(session,
                new TextMessage("{\"terminalId\":\"terminal-a\",\"status\":\"ONLINE\"}"));

        var message = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains(
                "\"type\":\"HEARTBEAT_ACK\"",
                "\"terminalId\":\"terminal-a\"",
                "\"lastPing\":\"2026-08-24T15:00:00Z\"");
    }

    @Test
    void rejeitaHeartbeatDeOutroTerminalSemAtualizarBanco() throws Exception {
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler();
        inject(handler, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
        TerminalService service = mock(TerminalService.class);
        inject(handler, "service", service);
        inject(handler, "authorization", mock(TerminalRequestAuthorizationService.class));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getPrincipal()).thenReturn(
                new AuthenticatedTerminal("terminal-a", "empresa-a", "condominio-a", 1));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handleTextMessage(session,
                new TextMessage("{\"terminalId\":\"terminal-b\",\"status\":\"ONLINE\"}")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(service, org.mockito.Mockito.never()).updateStatus(any());
        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
