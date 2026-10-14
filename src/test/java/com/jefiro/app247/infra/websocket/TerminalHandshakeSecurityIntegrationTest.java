package com.jefiro.app247.infra.websocket;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.config.TerminalHandshakeInterceptor;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import com.jefiro.app247.infra.service.EmpresaContext;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TerminalHandshakeSecurityIntegrationTest {
    @Autowired TerminalHandshakeInterceptor interceptor;
    @Autowired TerminalRequestAuthorizationService authorization;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired TerminalRepository terminals;
    private Terminal a;
    private Terminal b;
    private String credential;

    @BeforeEach
    void setup() {
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa " + UUID.randomUUID());
        empresa.setNomeFantasia("Empresa");
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail(UUID.randomUUID() + "@example.test");
        empresa = empresas.saveAndFlush(empresa);
        Condominio condominio = new Condominio();
        condominio.setNome("Condominio");
        condominio.setEmpresa(empresa);
        condominio = condominios.saveAndFlush(condominio);
        a = terminal(condominio, "A");
        b = terminal(condominio, "B");
        EmpresaContext.set(empresa.getId());
        credential = authorization.provision(a.getIdTerminal()).credential();
        EmpresaContext.clear();
    }

    @Test
    void terminalSocketAssociaPrincipalSemGuardarSecret() {
        Map<String, Object> attributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(request("/terminal-socket", credential),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes)).isTrue();
        assertThat(attributes.get(AuthenticatedTerminal.class.getName()))
                .isEqualTo(new AuthenticatedTerminal(a.getIdTerminal(),
                        a.getCondominio().getEmpresa().getId(), a.getCondominio().getIdCondominio(), 1));
        assertThat(attributes.values()).doesNotContain(credential);
    }

    @Test
    void paymentSocketRejeitaMesmoTenantOutroTerminalESemToken() {
        assertThatThrownBy(() -> interceptor.beforeHandshake(
                request("/payment-socket/" + b.getIdTerminal(), credential),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value()).isEqualTo(403);
        assertThatThrownBy(() -> interceptor.beforeHandshake(
                request("/payment-socket/" + a.getIdTerminal(), null),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void credentialRevogadaRejeitaNovoHandshake() {
        EmpresaContext.set(a.getCondominio().getEmpresa().getId());
        authorization.revoke(a.getIdTerminal());
        EmpresaContext.clear();
        assertThatThrownBy(() -> interceptor.beforeHandshake(request("/terminal-socket", credential),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value()).isEqualTo(401);
    }

    private Terminal terminal(Condominio condominio, String suffix) {
        Terminal terminal = new Terminal();
        terminal.setCondominio(condominio);
        terminal.setNome("Terminal " + suffix);
        terminal.setCodigo("terminal-" + suffix + "-" + UUID.randomUUID());
        terminal.setAtivo(true);
        terminal.setStatus(TerminalStatus.OFFLINE);
        terminal.setLifecycleState(TerminalLifecycleState.ACTIVE);
        return terminals.saveAndFlush(terminal);
    }

    private ServerHttpRequest request(String path, String value) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (value != null) headers.add("X-Terminal-Token", value);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
        return request;
    }
}
