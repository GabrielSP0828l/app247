package com.jefiro.app247.apresentacao.integracao;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permissões do administrador: rotas de gestão ({@code /admin}, {@code /produtos},
 * {@code /estoque-empresa}) exigem papel ADMIN ou GERENTE; o administrador só enxerga
 * usuários da própria empresa (isolamento multi-tenant).
 */
class AdministradorIntegrationTest extends IntegracaoBase {

    private User admin;
    private User usuarioComum;
    private User usuarioDaMesmaEmpresa;
    private User usuarioDeOutraEmpresa;

    @BeforeEach
    void cenario() {
        Empresa empresa = novaEmpresa();
        admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        usuarioComum = novoUsuario(empresa, "52998224725", RoleUser.USER);
        usuarioDaMesmaEmpresa = novoUsuario(empresa, "11144477735", RoleUser.USER);
        usuarioDeOutraEmpresa = novoUsuario(novaEmpresa(), "93541134780", RoleUser.USER);
    }

    @Test
    @DisplayName("Administrador acessa painel, produtos e estoque")
    void administradorAcessaRotasDeGestao() throws Exception {
        mvc.perform(get("/admin/dashboard").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/produtos").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/estoque-empresa").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuário comum é bloqueado (403) nas rotas de gestão")
    void usuarioComumNaoAcessaGestao() throws Exception {
        mvc.perform(get("/admin/dashboard").header("Authorization", bearer(usuarioComum))).andExpect(status().isForbidden());
        mvc.perform(get("/produtos").header("Authorization", bearer(usuarioComum))).andExpect(status().isForbidden());
        mvc.perform(get("/estoque-empresa").header("Authorization", bearer(usuarioComum))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem token a rota administrativa responde 401")
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Administrador consulta usuário da própria empresa, mas não de outra empresa")
    void administradorRespeitaIsolamentoDeEmpresa() throws Exception {
        mvc.perform(get("/user/{id}", usuarioDaMesmaEmpresa.getIdUser()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/user/{id}", usuarioDeOutraEmpresa.getIdUser()).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
