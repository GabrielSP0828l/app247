package com.jefiro.app247.apresentacao.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.event.listener.UserListener;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.RegistrationRateLimiter;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base dos testes de integração da apresentação.
 *
 * <p>Sobe a aplicação inteira com o perfil {@code test} (H2 em memória, Flyway e workers
 * desligados) e chama os endpoints reais via MockMvc, passando pelos filtros de segurança
 * JWT. Cada teste roda em uma transação desfeita ao final ({@code @Transactional}), então
 * nenhum dado persiste e nenhum banco real é tocado.
 */
@SpringBootTest(properties = {"spring.task.scheduling.enabled=false",
        "payment.reconciliation.startup.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class IntegracaoBase {

    protected static final String SENHA = "senha123";
    protected final ObjectMapper json = new ObjectMapper();

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected EmpresaRepository empresas;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected TokenService tokenService;

    // Dependências externas substituídas por mocks: limite de tentativas (Redis) e
    // listener de boas-vindas (envio de e-mail).
    @MockitoBean protected RegistrationRateLimiter registrationRateLimiter;
    @MockitoBean protected UserListener userListener;

    @BeforeEach
    void liberarRateLimit() {
        when(registrationRateLimiter.allow(any(), any(), any())).thenReturn(true);
    }

    protected Empresa novaEmpresa() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return empresas.saveAndFlush(Empresa.builder().razaoSocial("Mercadinho " + sufixo)
                .nomeFantasia("Mercadinho " + sufixo).cnpj("CNPJ-" + sufixo)
                .email(sufixo + "@empresa.test").ativo(true).build());
    }

    /** Cria um usuário com a senha {@link #SENHA} criptografada (BCrypt), como no cadastro real. */
    protected User novoUsuario(Empresa empresa, String cpf, RoleUser role) {
        return users.saveAndFlush(User.builder().empresa(empresa).nome("Pessoa").sobrenome("Teste")
                .email(UUID.randomUUID() + "@example.test").senha(passwordEncoder.encode(SENHA))
                .cpf(cpf).ativo(true).role(role).build());
    }

    protected String bearer(User user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    protected JsonNode corpo(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
