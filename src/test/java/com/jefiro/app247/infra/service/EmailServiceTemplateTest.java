package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;
import com.jefiro.app247.domain.model.dto.ValidateEmailRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Nome e e-mail vêm de cadastro público e não podem injetar HTML no corpo do e-mail. */
class EmailServiceTemplateTest {
    private static final String INJECAO = "<a href=\"https://evil.example\">Clique aqui</a>";
    private final EmailService service = new EmailService(null);

    @Test
    void validacaoDeEmailEscapaNome() {
        String html = service.gerarTemplateValidarEmail(new ValidateEmailRequest("ana@example.test", INJECAO, "123456"));

        assertThat(html).doesNotContain(INJECAO).doesNotContain("href=\"https://evil.example\"")
                .contains("&lt;a href=&quot;https://evil.example&quot;&gt;").contains("123456");
    }

    @Test
    void recuperacaoDeSenhaEscapaNome() {
        String html = service.gerarTemplate(new ValidateCodeRequest("123456", "52998224725", "ana@example.test", INJECAO));

        assertThat(html).doesNotContain(INJECAO).contains("&lt;a href=");
    }

    @Test
    void boasVindasEscapaNomeEEmail() {
        User user = new User();
        user.setNome(INJECAO);
        user.setEmail("ana@example.test<script>");
        user.setCreatedAt(Instant.parse("2026-10-18T12:00:00Z"));

        String html = service.gerarTemplateBoasVindas(user);

        assertThat(html).doesNotContain(INJECAO).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
