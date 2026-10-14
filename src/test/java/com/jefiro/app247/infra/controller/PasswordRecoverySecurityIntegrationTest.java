package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.ResetPasswordRequest;
import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.CustomerIdentityHashService;
import com.jefiro.app247.infra.service.PasswordRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PasswordRecoverySecurityIntegrationTest {
    private static final String CPF = "52998224725";
    private static final String UNKNOWN_CPF = "12345678909";
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.security.password-recovery.request-limit", () -> 5);
        registry.add("app.security.password-recovery.validate-limit", () -> 5);
        registry.add("app.security.password-recovery.reset-limit", () -> 5);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RedisTemplate<String, Object> objects;
    @Autowired StringRedisTemplate strings;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordEncoder encoder;
    @Autowired CustomerIdentityHashService hashes;

    @BeforeEach
    void setup() {
        strings.getConnectionFactory().getConnection().serverCommands().flushDb();
        users.getByCpf(CPF).ifPresent(users::delete);
        users.saveAndFlush(User.builder().nome("Cliente").email("recovery@example.test")
                .cpf(CPF).senha(encoder.encode("senha-antiga")).ativo(true).role(RoleUser.USER).build());
    }

    @Test
    void requestNaoEnumeraContaEAplicaRateLimit() throws Exception {
        String existing = mvc.perform(post("/user/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(post("/user/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + UNKNOWN_CPF + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(missing).isEqualTo(existing);

        for (int i = 1; i < 5; i++) request(UNKNOWN_CPF, 202);
        request(UNKNOWN_CPF, 429);
    }

    @Test
    void tokenEhHashTtlUsoUnicoESenhaUsaBCrypt() throws Exception {
        String token = issueToken();
        assertThat(token).doesNotContain(CPF);
        assertThat(strings.keys("app247:password-token:*")).hasSize(1)
                .allMatch(key -> !key.contains(token));

        reset(token).andExpect(status().isOk());
        reset(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
        assertThat(encoder.matches("senha-nova-segura", users.getByCpf(CPF).orElseThrow().getSenha())).isTrue();
    }

    @Test
    void tokenExpiradoOuInvalidoEhRejeitadoSemDetalheSensivel() throws Exception {
        String token = issueToken();
        String hash = hashes.hash("password-reset-token", token);
        strings.delete("app247:password-token:" + hash);
        reset(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
        reset("token-invalido").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
    }

    @Test
    void consumoConcorrenteDoTokenPermiteSomenteUmReset() throws Exception {
        String token = issueToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try {
                    recovery.resetPassword(new ResetPasswordRequest(CPF, token, "senha-concorrente"), "10.0.0.1");
                    return true;
                } catch (ApiBusinessException denied) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = executor.invokeAll(List.of(attempt, attempt));
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private String issueToken() throws Exception {
        mvc.perform(post("/user/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF + "\"}"))
                .andExpect(status().isAccepted());
        ValidateCodeRequest code = (ValidateCodeRequest) objects.opsForList().rightPop("recovery_queue");
        return com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/user/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF + "\",\"code\":\"" + code.code() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token) throws Exception {
        return mvc.perform(post("/user/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + CPF + "\",\"token\":\"" + token
                        + "\",\"novaSenha\":\"senha-nova-segura\"}"));
    }

    private void request(String cpf, int status) throws Exception {
        mvc.perform(post("/user/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status));
    }
}
