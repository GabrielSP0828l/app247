package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.enum_type.TerminalLifecycleState;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.TerminalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TerminalDeviceCredentialIntegrationTest {
    @Autowired TerminalRequestAuthorizationService authorization;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired TerminalRepository terminals;

    private Empresa empresaA;
    private Empresa empresaB;
    private Terminal terminalA1;
    private Terminal terminalA2;
    private Terminal terminalB;

    @BeforeEach
    void setup() {
        empresaA = empresa("A");
        empresaB = empresa("B");
        Condominio condominioA = condominio(empresaA, "A");
        Condominio condominioB = condominio(empresaB, "B");
        terminalA1 = terminal(condominioA, "A1");
        terminalA2 = terminal(condominioA, "A2");
        terminalB = terminal(condominioB, "B");
        EmpresaContext.set(empresaA.getId());
    }

    @AfterEach
    void clearContext() {
        EmpresaContext.clear();
    }

    @Test
    void provisionaHashEAutenticaExatamenteUmTerminal() {
        var issued = authorization.provision(terminalA1.getIdTerminal());
        terminals.flush();
        Terminal persisted = terminals.findById(terminalA1.getIdTerminal()).orElseThrow();

        assertThat(issued.credential()).startsWith("tdc_").hasSizeGreaterThan(40);
        assertThat(persisted.getDeviceCredentialHash()).hasSize(64).doesNotContain(issued.credential());
        assertThat(persisted.getDeviceCredentialVersion()).isEqualTo(1);
        assertThat(authorization.authenticateChannel(issued.credential()).terminalId())
                .isEqualTo(terminalA1.getIdTerminal());
        assertThatThrownBy(() -> authorization.authorize(issued.credential(), terminalA2.getIdTerminal()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value())
                .isEqualTo(403);
        assertThatThrownBy(() -> authorization.authorize(issued.credential(), terminalB.getIdTerminal()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void revogaUmTerminalSemAfetarOutro() {
        String credentialA = authorization.provision(terminalA1.getIdTerminal()).credential();
        String credentialA2 = authorization.provision(terminalA2.getIdTerminal()).credential();

        authorization.revoke(terminalA1.getIdTerminal());

        assertThatThrownBy(() -> authorization.authenticateChannel(credentialA))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value())
                .isEqualTo(401);
        assertThat(authorization.authenticateChannel(credentialA2).terminalId())
                .isEqualTo(terminalA2.getIdTerminal());
    }

    @Test
    void rotacaoInvalidaCredencialAnteriorSemAfetarOutroTerminal() {
        String oldCredential = authorization.provision(terminalA1.getIdTerminal()).credential();
        String otherCredential = authorization.provision(terminalA2.getIdTerminal()).credential();
        var rotated = authorization.rotate(terminalA1.getIdTerminal());

        assertThat(rotated.version()).isEqualTo(2);
        assertThatThrownBy(() -> authorization.authenticateChannel(oldCredential))
                .isInstanceOf(ApiBusinessException.class);
        assertThat(authorization.authenticateChannel(rotated.credential()).terminalId())
                .isEqualTo(terminalA1.getIdTerminal());
        assertThat(authorization.authenticateChannel(otherCredential).terminalId())
                .isEqualTo(terminalA2.getIdTerminal());
    }

    @Test
    void gestorNaoProvisionaTerminalDeOutroTenant() {
        assertThatThrownBy(() -> authorization.provision(terminalB.getIdTerminal()))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void terminalDesativadoNaoOperaMesmoComCredencialValida() {
        String credential = authorization.provision(terminalA1.getIdTerminal()).credential();
        terminalA1.setAtivo(false);
        terminals.saveAndFlush(terminalA1);

        assertThatThrownBy(() -> authorization.authenticateChannel(credential))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    private Empresa empresa(String suffix) {
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa " + suffix);
        empresa.setNomeFantasia("Empresa " + suffix);
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail(UUID.randomUUID() + "@example.test");
        empresa.setAtivo(true);
        return empresas.saveAndFlush(empresa);
    }

    private Condominio condominio(Empresa empresa, String suffix) {
        Condominio condominio = new Condominio();
        condominio.setNome("Condominio " + suffix);
        condominio.setEmpresa(empresa);
        condominio.setAtivo(true);
        return condominios.saveAndFlush(condominio);
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
}
