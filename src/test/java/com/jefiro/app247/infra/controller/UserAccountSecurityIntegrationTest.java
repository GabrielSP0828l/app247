package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edição de conta e troca de senha não podem servir para tomar a conta de outro usuário. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAccountSecurityIntegrationTest {
    private static final String SENHA = "senha-atual-123";

    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwordEncoder;

    private User admin;
    private User gerente;
    private User cliente;
    private String sufixo;

    @BeforeEach
    void setup() {
        // H2 é compartilhado entre classes de teste: CPF/e-mail precisam ser únicos por execução.
        sufixo = UUID.randomUUID().toString().substring(0, 8);
        Empresa empresa = empresa();
        admin = user(empresa, RoleUser.ADMIN, "admin-" + sufixo + "@example.test");
        gerente = user(empresa, RoleUser.GERENTE, "gerente-" + sufixo + "@example.test");
        cliente = user(empresa, RoleUser.USER, "cliente-" + sufixo + "@example.test");
    }

    @Test
    void gestorNaoAlteraEmailDeOutroUsuario() throws Exception {
        update(admin, cliente, "{\"email\":\"atacante@example.test\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_SELF_ONLY"));

        assertThat(users.findById(cliente.getIdUser()).orElseThrow().getEmail())
                .isEqualTo("cliente-" + sufixo + "@example.test");
    }

    @Test
    void gestorPodeReenviarEmailInalteradoJuntoComOutrosCampos() throws Exception {
        update(admin, cliente, "{\"email\":\"CLIENTE-" + sufixo + "@example.test\",\"nome\":\"Novo Nome\"}")
                .andExpect(status().isOk());

        User salvo = users.findById(cliente.getIdUser()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Novo Nome");
        assertThat(salvo.getEmail()).isEqualTo("cliente-" + sufixo + "@example.test");
    }

    @Test
    void gerenteNaoAlteraAdministrador() throws Exception {
        update(gerente, admin, "{\"ativo\":false}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_MANAGEMENT_FORBIDDEN"));

        assertThat(users.findById(admin.getIdUser()).orElseThrow().getAtivo()).isTrue();
    }

    @Test
    void gestorNaoDesativaAPropriaConta() throws Exception {
        update(admin, admin, "{\"ativo\":false}").andExpect(status().isOk());

        assertThat(users.findById(admin.getIdUser()).orElseThrow().getAtivo()).isTrue();
    }

    @Test
    void trocaDoProprioEmailNormalizaEExigeNovaVerificacao() throws Exception {
        cliente.setEmailVerificado(true);
        users.saveAndFlush(cliente);

        update(cliente, cliente, "{\"email\":\"Novo.Email-" + sufixo + "@Example.TEST\"}").andExpect(status().isOk());

        User salvo = users.findById(cliente.getIdUser()).orElseThrow();
        assertThat(salvo.getEmail()).isEqualTo("novo.email-" + sufixo + "@example.test");
        assertThat(salvo.getEmailVerificado()).isFalse();
    }

    @Test
    void trocaDoProprioEmailParaEmailExistenteRetornaConflito() throws Exception {
        update(cliente, cliente, "{\"email\":\"ADMIN-" + sufixo + "@example.test\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void edicaoRejeitaCamposInvalidos() throws Exception {
        update(cliente, cliente, "{\"nome\":\"   \"}").andExpect(status().isBadRequest());
        update(cliente, cliente, "{\"email\":\"nao-e-email\"}").andExpect(status().isBadRequest());
        update(cliente, cliente, "{\"telefone\":\"abc\"}").andExpect(status().isBadRequest());
        update(cliente, cliente, "{\"nome\":\"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void trocaDeSenhaValidaCampos() throws Exception {
        changePassword(cliente, SENHA, "").andExpect(status().isBadRequest());
        changePassword(cliente, SENHA, "curta").andExpect(status().isBadRequest());
        changePassword(cliente, SENHA, "x".repeat(73)).andExpect(status().isBadRequest());
        changePassword(cliente, null, "nova-senha-segura").andExpect(status().isBadRequest());
        changePassword(cliente, SENHA, SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));

        changePassword(cliente, SENHA, "nova-senha-segura").andExpect(status().isOk());
        assertThat(passwordEncoder.matches("nova-senha-segura",
                users.findById(cliente.getIdUser()).orElseThrow().getSenha())).isTrue();
    }

    @Test
    void envioEValidacaoDeCodigoValidamEntrada() throws Exception {
        mvc.perform(post("/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"nome\":\"Ana\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\",\"nome\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/validate-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\",\"code\":\"12ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/validate-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\"}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions update(User actor, User target, String json) throws Exception {
        return mvc.perform(put("/user/{id}", target.getIdUser())
                .header("Authorization", "Bearer " + tokens.generateToken(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(User user, String oldPassword, String newPassword) throws Exception {
        String old = oldPassword == null ? "null" : "\"" + oldPassword + "\"";
        return mvc.perform(post("/user/alterar-senha")
                .header("Authorization", "Bearer " + tokens.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.getIdUser() + "\",\"oldPassword\":" + old
                        + ",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private Empresa empresa() {
        Empresa e = new Empresa();
        e.setRazaoSocial("Empresa Conta");
        e.setNomeFantasia("Empresa Conta");
        e.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        e.setEmail(UUID.randomUUID() + "@example.test");
        e.setAtivo(true);
        return empresas.saveAndFlush(e);
    }

    private User user(Empresa empresa, RoleUser role, String email) {
        String cpf = String.format("%011d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000_000L));
        return users.saveAndFlush(User.builder().empresa(empresa).nome("Pessoa").email(email)
                .senha(passwordEncoder.encode(SENHA)).cpf(cpf).ativo(true).role(role).build());
    }
}
