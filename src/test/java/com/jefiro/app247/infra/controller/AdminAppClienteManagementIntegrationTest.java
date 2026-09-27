package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.LoyaltyAccount;
import com.jefiro.app247.domain.model.appclient.RewardDiscountType;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.CustomerCouponRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.LoyaltyAccountRepository;
import com.jefiro.app247.infra.repository.LoyaltyMovementRepository;
import com.jefiro.app247.infra.repository.RewardRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.AdminAppClienteManagementService;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP -> Security -> service real -> JPA -> MySQL/Flyway, sem mock do service principal. */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "app247.workers.enabled=false",
        "payment.reconciliation.startup.enabled=false",
        "terminal.telemetry.offline-check-delay-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminAppClienteManagementIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.4")
            .withDatabaseName("app247_admin_test")
            .withUsername("app247")
            .withPassword("app247");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    private static final AtomicLong SEQUENCE = new AtomicLong(30000000000L);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenService tokens;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired UserRepository users;
    @Autowired RewardRepository rewards;
    @Autowired LoyaltyAccountRepository accounts;
    @Autowired LoyaltyMovementRepository movements;
    @Autowired CustomerCouponRepository coupons;
    @Autowired AdminAppClienteManagementService managementService;
    @MockitoBean StringRedisTemplate redis;

    private Empresa empresaA;
    private Empresa empresaB;
    private Condominio condominioA;
    private Condominio condominioB;
    private User adminA;
    private User gerenteA;
    private User clienteA;
    private User adminB;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.getAndAdd(20);
        empresaA = empresa("Empresa A " + sequence, sequence);
        empresaB = empresa("Empresa B " + sequence, sequence + 1);
        condominioA = condominio(empresaA, "Condomínio A");
        condominioB = condominio(empresaB, "Condomínio B");
        adminA = user(empresaA, condominioA, RoleUser.ADMIN, sequence + 2);
        gerenteA = user(empresaA, condominioA, RoleUser.GERENTE, sequence + 3);
        clienteA = user(empresaA, condominioA, RoleUser.USER, sequence + 4);
        adminB = user(empresaB, condominioB, RoleUser.ADMIN, sequence + 5);
    }

    @Test
    void somenteAdminEGerenteAcessamRotasDeGestao() throws Exception {
        mvc.perform(get("/admin/app-cliente/config").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.catalogoAtivo").value(false));
        mvc.perform(get("/admin/app-cliente/config").header(HttpHeaders.AUTHORIZATION, bearer(gerenteA)))
                .andExpect(status().isOk());

        for (RoleUser role : List.of(RoleUser.USER, RoleUser.MORADOR, RoleUser.PORTARIA)) {
            User restricted = user(empresaA, condominioA, role, SEQUENCE.getAndIncrement());
            mvc.perform(get("/admin/app-cliente/config").header(HttpHeaders.AUTHORIZATION, bearer(restricted)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/app-cliente/config")).andExpect(status().isForbidden());
        assertThat(managementService.getClass().getName()).doesNotContain("MockitoMock");
    }

    @Test
    void configuracaoEhPorEmpresaEFlagsDoBootstrapSaoDerivadas() throws Exception {
        putPoints(adminA, true, "1.000000");
        mvc.perform(put("/admin/app-cliente/config")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appConfig(true, true, true, true, true, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogoAtivo").value(true))
                .andExpect(jsonPath("$.fidelidadeAtiva").value(true));

        mvc.perform(get("/app-cliente/bootstrap").header(HttpHeaders.AUTHORIZATION, bearer(clienteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.catalogo").value(true))
                .andExpect(jsonPath("$.features.promocoes").value(true))
                .andExpect(jsonPath("$.features.fidelidade").value(true))
                .andExpect(jsonPath("$.features.pontos").value(true))
                .andExpect(jsonPath("$.features.cashback").value(false))
                .andExpect(jsonPath("$.features.recompensas").value(true))
                .andExpect(jsonPath("$.features.cupons").value(true));

        mvc.perform(get("/admin/app-cliente/config").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogoAtivo").value(false))
                .andExpect(jsonPath("$.fidelidadeAtiva").value(false));
        mvc.perform(get("/app-cliente/catalogo").header(HttpHeaders.AUTHORIZATION, bearer(user(
                        empresaB, condominioB, RoleUser.USER, SEQUENCE.getAndIncrement()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_DISABLED"));

        LoyaltyAccount account = new LoyaltyAccount();
        account.setEmpresa(empresaA); account.setUser(clienteA); account.setPointsBalance(321);
        accounts.saveAndFlush(account);
        putPoints(adminA, false, "1.000000");
        mvc.perform(get("/app-cliente/bootstrap").header(HttpHeaders.AUTHORIZATION, bearer(clienteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.fidelidade").value(false))
                .andExpect(jsonPath("$.features.pontos").value(false))
                .andExpect(jsonPath("$.features.cashback").value(false))
                .andExpect(jsonPath("$.features.recompensas").value(false))
                .andExpect(jsonPath("$.features.cupons").value(false));
        assertThat(accounts.findById(account.getId())).get().extracting(LoyaltyAccount::getPointsBalance).isEqualTo(321L);
    }

    @Test
    void fidelidadeValidaNonePointsCashbackEPercentuais() throws Exception {
        putPoints(adminA, true, "1.250000")
                .andExpect(jsonPath("$.modo").value("POINTS"))
                .andExpect(jsonPath("$.pontosPorReal").value(1.25));

        mvc.perform(put("/admin/app-cliente/fidelidade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":true,\"modo\":\"CASHBACK\",\"pontosPorReal\":null,\"cashbackPercentual\":\"2.000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("CASHBACK"))
                .andExpect(jsonPath("$.cashbackPercentual").value(2.0));
        mvc.perform(get("/app-cliente/bootstrap").header(HttpHeaders.AUTHORIZATION, bearer(clienteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.fidelidade").value(true))
                .andExpect(jsonPath("$.features.pontos").value(false))
                .andExpect(jsonPath("$.features.cashback").value(true))
                .andExpect(jsonPath("$.features.recompensas").value(false))
                .andExpect(jsonPath("$.features.cupons").value(false));
        mvc.perform(get("/app-cliente/fidelidade").header(HttpHeaders.AUTHORIZATION, bearer(clienteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mode").value("CASHBACK"));

        for (String body : List.of(
                "{\"ativo\":true,\"modo\":\"POINTS\",\"pontosPorReal\":null,\"cashbackPercentual\":null}",
                "{\"ativo\":true,\"modo\":\"CASHBACK\",\"pontosPorReal\":null,\"cashbackPercentual\":null}",
                "{\"ativo\":true,\"modo\":\"CASHBACK\",\"pontosPorReal\":null,\"cashbackPercentual\":\"-2\"}",
                "{\"ativo\":true,\"modo\":\"CASHBACK\",\"pontosPorReal\":null,\"cashbackPercentual\":\"150\"}")) {
            mvc.perform(put("/admin/app-cliente/fidelidade")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LOYALTY_CONFIG"));
        }
        mvc.perform(put("/admin/app-cliente/fidelidade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":true,\"modo\":\"POINTS_AND_CASHBACK\",\"pontosPorReal\":\"1\",\"cashbackPercentual\":\"2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOYALTY_MODE"));
        mvc.perform(put("/admin/app-cliente/fidelidade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false,\"modo\":\"NONE\",\"pontosPorReal\":null,\"cashbackPercentual\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modo").value("NONE"));
    }

    @Test
    void crudDeRecompensaMantemBigDecimalSoftDeleteETenantOpaco() throws Exception {
        String createBody = percentageReward("10% OFF", "10.000000", 500, "100.000000", "25.000000");
        JsonNode created = body(mvc.perform(post("/admin/app-cliente/recompensas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDesconto").value("PERCENTUAL"))
                .andExpect(jsonPath("$.valorDesconto").value(10.0))
                .andExpect(jsonPath("$.valorMinimoCompra").value(100.0))
                .andExpect(jsonPath("$.descontoMaximo").value(25.0)).andReturn());
        String id = created.get("id").asText();
        assertThat(rewards.findById(id).orElseThrow().getValorDesconto()).isEqualByComparingTo("10.000000");
        assertThat(rewards.findById(id).orElseThrow().getValorMinimoCompra()).isEqualByComparingTo("100.000000");
        assertThat(rewards.findById(id).orElseThrow().getDescontoMaximo()).isEqualByComparingTo("25.000000");

        mvc.perform(get("/admin/app-cliente/recompensas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REWARD_NOT_FOUND"));
        mvc.perform(put("/admin/app-cliente/recompensas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/admin/app-cliente/recompensas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());

        String fixed = fixedReward("R$ 15 OFF", "15.000000", 400, "80.000000");
        mvc.perform(put("/admin/app-cliente/recompensas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(gerenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(fixed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipoDesconto").value("VALOR_FIXO"));
        mvc.perform(get("/admin/app-cliente/recompensas").queryParam("ativo", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/admin/app-cliente/recompensas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isNoContent());
        assertThat(rewards.findById(id)).get().extracting(reward -> reward.isAtivo()).isEqualTo(false);
    }

    @Test
    void recompensaRejeitaRegrasFinanceirasInvalidas() throws Exception {
        List<String> invalid = List.of(
                percentageReward("Custo zero", "10", 0, "100", "25"),
                percentageReward("Mínimo negativo", "10", 500, "-1", "25"),
                percentageReward("Percentual 150", "150", 500, "100", "25"),
                percentageReward("Máximo zero", "10", 500, "100", "0"));
        for (String body : invalid) {
            mvc.perform(post("/admin/app-cliente/recompensas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REWARD"));
        }
    }

    @Test
    void resgateEhTenantAwareECupomPreservaSnapshotAposEdicao() throws Exception {
        putPoints(adminA, true, "1.000000");
        LoyaltyAccount account = new LoyaltyAccount();
        account.setEmpresa(empresaA); account.setUser(clienteA); account.setPointsBalance(1000);
        accounts.saveAndFlush(account);

        String rewardId = body(mvc.perform(post("/admin/app-cliente/recompensas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageReward("10% OFF", "10.000000", 500, "100.000000", "25.000000")))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String foreignRewardId = body(mvc.perform(post("/admin/app-cliente/recompensas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedReward("Externa", "5", 100, "0")))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        mvc.perform(get("/app-cliente/recompensas").header(HttpHeaders.AUTHORIZATION, bearer(clienteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rewardId))
                .andExpect(jsonPath("$[0].eligible").value(true));
        mvc.perform(post("/app-cliente/recompensas/{id}/resgates", foreignRewardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clienteA))
                        .header("Idempotency-Key", "foreign-" + rewardId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REWARD_NOT_FOUND"));

        JsonNode redemption = body(mvc.perform(post("/app-cliente/recompensas/{id}/resgates", rewardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clienteA))
                        .header("Idempotency-Key", "snapshot-" + rewardId))
                .andExpect(status().isCreated()).andReturn());
        String couponId = redemption.get("couponId").asText();

        mvc.perform(put("/admin/app-cliente/recompensas/{id}", rewardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageReward("15% OFF", "15.000000", 500, "120.000000", "30.000000")))
                .andExpect(status().isOk());

        CustomerCoupon coupon = coupons.findById(couponId).orElseThrow();
        assertThat(coupon.getNome()).isEqualTo("10% OFF");
        assertThat(coupon.getTipo()).isEqualTo(RewardDiscountType.PERCENTUAL);
        assertThat(coupon.getValor()).isEqualByComparingTo("10.000000");
        assertThat(coupon.getValorMinimoCompra()).isEqualByComparingTo("100.000000");
        assertThat(coupon.getDescontoMaximo()).isEqualByComparingTo("25.000000");
        assertThat(coupon.getReward().getId()).isEqualTo(rewardId);
        assertThat(movements.findTop50ByAccountIdOrderByCreatedAtDesc(account.getId())).hasSize(1)
                .allMatch(movement -> movement.getEmpresa().getId().equals(empresaA.getId()));
    }

    @Test
    void openApiPublicaTodasAsRotasAdministrativas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/config'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/config'].put").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/fidelidade'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/fidelidade'].put").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/recompensas'].post").exists())
                .andExpect(jsonPath("$.paths['/admin/app-cliente/recompensas/{id}'].delete").exists());
    }

    private org.springframework.test.web.servlet.ResultActions putPoints(User manager, boolean active, String rate) throws Exception {
        return mvc.perform(put("/admin/app-cliente/fidelidade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":" + active + ",\"modo\":\"POINTS\",\"pontosPorReal\":\"" + rate
                                + "\",\"cashbackPercentual\":null}"))
                .andExpect(status().isOk());
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private Empresa empresa(String name, long sequence) {
        String cnpj = String.format("%014d", sequence % 100_000_000_000_000L);
        return empresas.saveAndFlush(Empresa.builder().razaoSocial(name).nomeFantasia(name).cnpj(cnpj)
                .email(sequence + "@empresa.test").ativo(true).build());
    }

    private Condominio condominio(Empresa empresa, String name) {
        Condominio condominio = new Condominio();
        condominio.setEmpresa(empresa); condominio.setNome(name); condominio.setAtivo(true);
        return condominios.saveAndFlush(condominio);
    }

    private User user(Empresa empresa, Condominio condominio, RoleUser role, long sequence) {
        String cpf = String.format("%011d", sequence % 100_000_000_000L);
        return users.saveAndFlush(User.builder().empresa(empresa).condominio(condominio).nome(role.name())
                .email(sequence + "@user.test").senha("{noop}senha").cpf(cpf).ativo(true)
                .emailVerificado(false).role(role).build());
    }

    private static String appConfig(boolean catalog, boolean promotions, boolean loyalty,
                                    boolean qr, boolean preferences, boolean support) {
        return "{\"catalogoAtivo\":" + catalog + ",\"promocoesAtivas\":" + promotions
                + ",\"fidelidadeAtiva\":" + loyalty + ",\"qrIdentificacaoAtivo\":" + qr
                + ",\"preferenciasAtivas\":" + preferences + ",\"suporteAtivo\":" + support + "}";
    }

    private static String percentageReward(String name, String value, long points, String minimum, String maximum) {
        return reward(name, "PERCENTUAL", value, points, minimum, maximum);
    }

    private static String fixedReward(String name, String value, long points, String minimum) {
        return reward(name, "VALOR_FIXO", value, points, minimum, null);
    }

    private static String reward(String name, String type, String value, long points, String minimum, String maximum) {
        return "{\"nome\":\"" + name + "\",\"descricao\":\"Benefício de teste\",\"tipoDesconto\":\"" + type
                + "\",\"valorDesconto\":\"" + value + "\",\"custoPontos\":" + points
                + ",\"valorMinimoCompra\":\"" + minimum + "\",\"descontoMaximo\":"
                + (maximum == null ? "null" : "\"" + maximum + "\"")
                + ",\"validadeDias\":30,\"limiteUsoPorUsuario\":1,\"ativo\":true}";
    }
}
