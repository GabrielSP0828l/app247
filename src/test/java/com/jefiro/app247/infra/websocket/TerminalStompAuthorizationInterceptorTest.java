package com.jefiro.app247.infra.websocket;

import com.jefiro.app247.infra.config.TerminalStompAuthorizationInterceptor;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;

class TerminalStompAuthorizationInterceptorTest {
    private final TerminalStompAuthorizationInterceptor interceptor =
            new TerminalStompAuthorizationInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void configureAuthorization() {
        TerminalRequestAuthorizationService authorization = mock(TerminalRequestAuthorizationService.class);
        when(authorization.revalidate(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> call.getArgument(0));
        ReflectionTestUtils.setField(interceptor, "authorization", authorization);
    }

    @Test
    void connectExigePrincipalDoHandshake() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.CONNECT, null, null), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(interceptor.preSend(message(StompCommand.CONNECT, null, identity("a")), channel))
                .isNotNull();
    }

    @Test
    void subscribePermiteSomenteTopicoDoProprioTerminal() {
        assertThat(interceptor.preSend(message(StompCommand.SUBSCRIBE,
                "/topic/payment/a", identity("a")), channel)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE,
                "/topic/payment/b", identity("a")), channel)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendEhNegadoPorqueNaoHaDestinationInbound() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND,
                "/app/terminal/a/heartbeat", identity("a")), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND,
                "/app/terminal/b/heartbeat", identity("a")), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> message(StompCommand command, String destination,
                                    AuthenticatedTerminal terminal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(terminal);
        if (destination != null) accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private AuthenticatedTerminal identity(String terminalId) {
        return new AuthenticatedTerminal(terminalId, "empresa", "condominio", 1);
    }
}
