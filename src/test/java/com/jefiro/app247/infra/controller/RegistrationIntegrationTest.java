package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.event.listener.UserListener;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.RegistrationRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.task.scheduling.enabled=false","payment.reconciliation.startup.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean RegistrationRateLimiter rateLimiter;
    @MockitoBean UserListener userListener;

    @BeforeEach void allowRequests() {
        when(rateLimiter.allow(any(), any(), any())).thenReturn(true);
    }

    @Test void cadastroValidoNormalizaDadosUsaBcryptENaoVinculaTenant() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "  Maria   da Silva  ", "529.982.247-25", " MARIA@EXAMPLE.COM ", "(75) 99999-9999", "segredo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpfVerificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.requiresLogin").value(true))
                .andExpect(jsonPath("$.condominio").doesNotExist());

        User user = users.getByCpf("52998224725").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("maria@example.com");
        assertThat(user.getTelefone()).isEqualTo("75999999999");
        assertThat(user.getRole()).isEqualTo(RoleUser.USER);
        assertThat(user.getEmpresa()).isNull();
        assertThat(user.getCondominio()).isNull();
        assertThat(user.getCpfVerifiedAt()).isNull();
        assertThat(user.getSenha()).isNotEqualTo("segredo");
        assertThat(passwords.matches("segredo", user.getSenha())).isTrue();
    }

    @Test void aceitaCpfSemMascara() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Ana Souza", "11144477735", "ana@example.com", null, "segredo")))
                .andExpect(status().isCreated());
        assertThat(users.existsByCpf("11144477735")).isTrue();
    }

    @Test void contaGlobalFazLoginESolicitaVinculoAntesDoBootstrap() throws Exception {
        String cpf = "16899535009";
        register(cpf, "global-login@example.com");

        String loginBody = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\",\"senha\":\"segredo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginBody).get("token").asText();

        mvc.perform(get("/app-cliente/bootstrap").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LINK_REQUIRED"));
    }

    @Test void rejeitaFormularioVazioCpfEmailESenhaInvalidos() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Nome", "123", "email-invalido", null, "123")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"));
    }

    @Test void rejeitaCpfMatematicamenteInvalido() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Cliente", "11111111111", "cpf-invalid@example.com", null, "segredo")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CPF"));
    }

    @Test void cpfEEmailDuplicadosRetornam409Estruturado() throws Exception {
        register("12345678909", "duplicado@example.com");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Outra", "123.456.789-09", "outra@example.com", null, "segredo")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CPF_ALREADY_REGISTERED"));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Outra", "93541134780", "DUPLICADO@example.com", null, "segredo")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test void camposDeEscalacaoSaoIgnoradosESempreCriamUserGlobal() throws Exception {
        String malicious = payload("Atacante", "98765432100", "safe@example.com", null, "segredo")
                .replace("}", ",\"role\":\"ADMIN\",\"empresaId\":\"empresa-b\",\"condominioId\":\"cond-b\"}");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(malicious))
                .andExpect(status().isCreated());
        User user = users.getByCpf("98765432100").orElseThrow();
        assertThat(user.getRole()).isEqualTo(RoleUser.USER);
        assertThat(user.getEmpresa()).isNull();
        assertThat(user.getCondominio()).isNull();
    }

    @Test void configuracaoPublicaNaoInventaUrls() throws Exception {
        mvc.perform(get("/public/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalDocumentsConfigured").value(false))
                .andExpect(jsonPath("$.operationalGap").value("LEGAL_DOCUMENTS_NOT_CONFIGURED"));
    }

    @Test void requestsConcorrentesProduzemUmaCriacaoEUmConflito() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.of(1, 2).stream().map(n -> pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload(
                        "Concorrente", "39053344705", "race@example.com", null, "segredo")))
                        .andReturn().getResponse().getStatus();
            })).toList();
            ready.await();
            start.countDown();
            List<Integer> statuses = tasks.stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).sorted().toList();
            assertThat(statuses).containsExactly(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    private void register(String cpf, String email) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(payload("Cliente", cpf, email, null, "segredo"))).andExpect(status().isCreated());
    }

    private static String payload(String nome, String cpf, String email, String telefone, String senha) {
        return "{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf + "\",\"email\":\"" + email
                + "\",\"telefone\":" + (telefone == null ? "null" : "\"" + telefone + "\"")
                + ",\"senha\":\"" + senha + "\"}";
    }
}
