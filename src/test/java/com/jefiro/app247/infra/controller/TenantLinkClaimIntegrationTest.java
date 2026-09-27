package com.jefiro.app247.infra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.service.CustomerIdentityHashService;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "payment.reconciliation.startup.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantLinkClaimIntegrationTest {
    private static final List<String> VALID_CPFS = List.of(
            "52998224725", "11144477735", "12345678909",
            "93541134780", "98765432100", "39053344705");
    private static final AtomicInteger CPF_INDEX = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong RAW_CPF_INDEX =
            new java.util.concurrent.atomic.AtomicLong(70000000000L);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TokenService tokens;
    @Autowired CustomerIdentityHashService hashes;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired UserRepository users;
    @Autowired CustomerIdentityRepository identities;
    @Autowired TerminalRepository terminals;
    @Autowired CarrinhoRepository carts;
    @Autowired OrderRepository orders;
    @Autowired LoyaltyAccountRepository loyaltyAccounts;
    @Autowired LoyaltyMovementRepository loyaltyMovements;

    @MockitoBean RedisTemplate<String, Object> redisTemplate;
    @MockitoBean StringRedisTemplate stringRedisTemplate;

    private Empresa empresaA;
    private Empresa empresaB;
    private Condominio condominioA;
    private Condominio condominioB;
    private User managerA;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ListOperations<String, Object> lists = org.mockito.Mockito.mock(ListOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForList()).thenReturn(lists);
        empresaA = empresa("A");
        empresaB = empresa("B");
        condominioA = condominio(empresaA, "A");
        condominioB = condominio(empresaB, "B");
        managerA = user(empresaA, condominioA, uniqueRawCpf(), RoleUser.ADMIN);
        user(empresaB, condominioB, uniqueRawCpf(), RoleUser.ADMIN);
    }

    @Test
    void convitePreValidadoVinculaVerificaCpfEClaimEhIdempotenteETenantAware() throws Exception {
        String cpf = nextValidCpf();
        User client = user(null, null, cpf, RoleUser.USER);
        CustomerIdentity identityA = identity(empresaA, cpf);
        CustomerIdentity identityB = identity(empresaB, cpf);
        String orderA = historicalOrder(empresaA, condominioA, identityA);
        String orderB = historicalOrder(empresaB, condominioB, identityB);

        String managerAuthorization = bearer(managerA);
        String globalAuthorization = bearer(client);

        mvc.perform(get("/app-cliente/compras").header(HttpHeaders.AUTHORIZATION, globalAuthorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_LINK_REQUIRED"));

        String invitationBody = mvc.perform(post("/admin/app-cliente/tenant-invitations")
                        .header(HttpHeaders.AUTHORIZATION, managerAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\",\"condominioId\":\"" + condominioA.getIdCondominio() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationToken = objectMapper.readTree(invitationBody).get("token").asText();

        String consumeBody = mvc.perform(post("/app-cliente/tenant-link")
                        .header(HttpHeaders.AUTHORIZATION, globalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + invitationToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINKED"))
                .andExpect(jsonPath("$.cpfVerificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.historicalPurchasesClaimed").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode consume = objectMapper.readTree(consumeBody);
        String linkedAuthorization = "Bearer " + consume.get("accessToken").asText();

        mvc.perform(get("/app-cliente/bootstrap").header(HttpHeaders.AUTHORIZATION, globalAuthorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));

        mvc.perform(get("/app-cliente/compras").header(HttpHeaders.AUTHORIZATION, linkedAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderA));
        mvc.perform(get("/app-cliente/compras/{id}", orderB)
                        .header(HttpHeaders.AUTHORIZATION, linkedAuthorization))
                .andExpect(status().isNotFound());

        mvc.perform(post("/app-cliente/tenant-link")
                        .header(HttpHeaders.AUTHORIZATION, linkedAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + invitationToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicalPurchasesClaimed").value(true));

        User linked = users.findById(client.getIdUser()).orElseThrow();
        assertThat(linked.getEmpresa().getId()).isEqualTo(empresaA.getId());
        assertThat(linked.getCondominio().getIdCondominio()).isEqualTo(condominioA.getIdCondominio());
        assertThat(linked.getCpfVerifiedAt()).isNotNull();
        assertThat(identities.findById(identityA.getId()).orElseThrow().getUser().getIdUser())
                .isEqualTo(client.getIdUser());
        assertThat(identities.findById(identityB.getId()).orElseThrow().getUser()).isNull();
        assertThat(loyaltyAccounts.findAll()).isEmpty();
        assertThat(loyaltyMovements.findAll()).isEmpty();
    }

    @Test
    void cpfUnverifiedNaoEnxergaHistoricoMesmoComMesmoCpf() throws Exception {
        String cpf = nextValidCpf();
        User client = user(empresaA, condominioA, cpf, RoleUser.USER);
        CustomerIdentity identity = identity(empresaA, cpf);
        historicalOrder(empresaA, condominioA, identity);

        mvc.perform(get("/app-cliente/compras").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(users.findById(client.getIdUser()).orElseThrow().getCpfVerifiedAt()).isNull();
        assertThat(identities.findById(identity.getId()).orElseThrow().getUser()).isNull();
    }

    @Test
    void conviteDeOutroCpfOuCondominioDeOutraEmpresaNaoVinculaENaoRevelaTenant() throws Exception {
        String invitedCpf = nextValidCpf();
        String otherCpf = nextValidCpf();
        User otherClient = user(null, null, otherCpf, RoleUser.USER);

        mvc.perform(post("/admin/app-cliente/tenant-invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + invitedCpf + "\",\"condominioId\":\"" + condominioB.getIdCondominio() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONDOMINIUM_NOT_FOUND"));

        String body = mvc.perform(post("/admin/app-cliente/tenant-invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + invitedCpf + "\",\"condominioId\":\"" + condominioA.getIdCondominio() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String invitationToken = objectMapper.readTree(body).get("token").asText();

        mvc.perform(post("/app-cliente/tenant-link")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + invitationToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_INVITATION_INVALID"));

        User unchanged = users.findById(otherClient.getIdUser()).orElseThrow();
        assertThat(unchanged.getEmpresa()).isNull();
        assertThat(unchanged.getCondominio()).isNull();
        assertThat(unchanged.getCpfVerifiedAt()).isNull();
    }

    @Test
    void rotasDeVinculoExigemAutenticacaoEPapelCorreto() throws Exception {
        String cpf = nextValidCpf();
        User client = user(null, null, cpf, RoleUser.USER);

        mvc.perform(post("/app-cliente/tenant-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-inexistente\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/app-cliente/tenant-invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\",\"condominioId\":\""
                                + condominioA.getIdCondominio() + "\"}"))
                .andExpect(status().isForbidden());
    }

    private String historicalOrder(Empresa company, Condominio condo, CustomerIdentity identity) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Terminal terminal = new Terminal(); terminal.setCondominio(condo); terminal.setNome("Terminal " + suffix);
        terminal.setCodigo("T-" + suffix); terminal.setStatus(TerminalStatus.ONLINE);
        terminal = terminals.saveAndFlush(terminal);
        Carrinho cart = new Carrinho(); cart.setTerminal(terminal); cart.setCustomerIdentity(identity);
        cart.setStatus(CarrinhoStatus.PAID); cart.setSubtotal(BigDecimal.TEN); cart = carts.saveAndFlush(cart);
        Order order = new Order(); order.setEmpresa(company); order.setCondominio(condo); order.setTerminal(terminal);
        order.setCarrinho(cart); order.setCustomerIdentity(identity); order.setSubtotal(BigDecimal.TEN);
        order.setDesconto(BigDecimal.ZERO); order.setTotalCalculado(BigDecimal.TEN); order.setTotalCobrado(BigDecimal.TEN);
        order.setOriginRequest(OriginRequest.TERMINAL); order.setStatus(OrderStatus.PROCESSED); order.setPaidAt(Instant.now());
        return orders.saveAndFlush(order).getIdOrder();
    }

    private CustomerIdentity identity(Empresa company, String cpf) {
        CustomerIdentity value = new CustomerIdentity(); value.setEmpresa(company); value.setCpfHash(hashes.hashCpf(cpf));
        return identities.saveAndFlush(value);
    }

    private Empresa empresa(String label) {
        String suffix = UUID.randomUUID().toString();
        return empresas.saveAndFlush(Empresa.builder().razaoSocial("Empresa " + label).nomeFantasia("Empresa " + label)
                .cnpj((suffix.replaceAll("\\D", "") + "12345678901234").substring(0, 14))
                .email(suffix + "@empresa.test").ativo(true).build());
    }

    private Condominio condominio(Empresa company, String label) {
        Condominio value = new Condominio(); value.setEmpresa(company); value.setNome("Condomínio " + label); value.setAtivo(true);
        return condominios.saveAndFlush(value);
    }

    private User user(Empresa company, Condominio condo, String cpf, RoleUser role) {
        return users.saveAndFlush(User.builder().empresa(company).condominio(condo).nome("Usuário")
                .email(UUID.randomUUID() + "@user.test").senha("{noop}senha").cpf(cpf)
                .ativo(true).emailVerificado(false).role(role).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private static String nextValidCpf() {
        int index = CPF_INDEX.getAndIncrement();
        if (index >= VALID_CPFS.size()) throw new IllegalStateException("Lista de CPFs de teste esgotada");
        return VALID_CPFS.get(index);
    }

    private static String uniqueRawCpf() {
        return Long.toString(RAW_CPF_INDEX.getAndIncrement());
    }
}
