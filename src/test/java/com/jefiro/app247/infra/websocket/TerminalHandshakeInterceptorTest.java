package com.jefiro.app247.infra.config;

import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;

class TerminalHandshakeInterceptorTest {
    @Test
    void paymentSocketAuthenticatesCredentialAndPathTerminalBeforeUpgrade() {
        var authorization = mock(TerminalRequestAuthorizationService.class);
        var interceptor = new TerminalHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "authorization", authorization);
        var request = request("/payment-socket/terminal-a", "test-secret");

        when(authorization.authorizeChannel("test-secret", "terminal-a"))
                .thenReturn(identity("terminal-a"));
        assertTrue(interceptor.beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class),
                new HashMap<>()));

        verify(authorization).authorizeChannel("test-secret", "terminal-a");
    }

    @Test
    void heartbeatSocketRequiresAuthenticatedInternalChannel() {
        var authorization = mock(TerminalRequestAuthorizationService.class);
        var interceptor = new TerminalHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "authorization", authorization);
        var request = request("/terminal-socket", "test-secret");

        when(authorization.authenticateChannel("test-secret")).thenReturn(identity("terminal-a"));
        assertTrue(interceptor.beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class),
                new HashMap<>()));

        verify(authorization).authenticateChannel("test-secret");
    }

    private AuthenticatedTerminal identity(String id) {
        return new AuthenticatedTerminal(id, "empresa-a", "condominio-a", 1);
    }

    private ServerHttpRequest request(String path, String credential) {
        var request = mock(ServerHttpRequest.class);
        var headers = new HttpHeaders();
        headers.add("X-Terminal-Token", credential);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
        return request;
    }
}
