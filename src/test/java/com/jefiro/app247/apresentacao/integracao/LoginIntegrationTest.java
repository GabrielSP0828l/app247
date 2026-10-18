package com.jefiro.app247.apresentacao.integracao;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integração da tela de login: {@code POST /auth/login} e {@code POST /auth/login/admin}. */
class LoginIntegrationTest extends IntegracaoBase {

    private static final String CPF_USUARIO = "52998224725";
    private static final String CPF_ADMIN = "39053344705";

    @BeforeEach
    void usuarios() {
        Empresa empresa = novaEmpresa();
        novoUsuario(empresa, CPF_USUARIO, RoleUser.USER);
        novoUsuario(empresa, CPF_ADMIN, RoleUser.ADMIN);
    }

    @Test
    @DisplayName("Credenciais corretas: 200 com token JWT e dados do usuário")
    void loginComSucesso() throws Exception {
        mvc.perform(login("/auth/login", CPF_USUARIO, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.cpf").value(CPF_USUARIO));
    }

    @Test
    @DisplayName("CPF com máscara também é aceito no login")
    void loginComCpfFormatado() throws Exception {
        mvc.perform(login("/auth/login", "529.982.247-25", SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Senha incorreta: 401 sem token")
    void senhaIncorreta() throws Exception {
        mvc.perform(login("/auth/login", CPF_USUARIO, "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message").value("CPF ou senha inválidos"));
    }

    @Test
    @DisplayName("CPF não cadastrado: 401 com a mesma mensagem (não revela se o CPF existe)")
    void cpfNaoCadastrado() throws Exception {
        mvc.perform(login("/auth/login", "11144477735", SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("CPF ou senha inválidos"));
    }

    @Test
    @DisplayName("Campos vazios: 400")
    void camposVazios() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login administrativo: ADMIN entra, usuário comum recebe 403")
    void loginAdministrativo() throws Exception {
        mvc.perform(login("/auth/login/admin", CPF_ADMIN, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(login("/auth/login/admin", CPF_USUARIO, SENHA))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String rota, String cpf, String senha) {
        return post(rota).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + cpf + "\",\"senha\":\"" + senha + "\"}");
    }
}
