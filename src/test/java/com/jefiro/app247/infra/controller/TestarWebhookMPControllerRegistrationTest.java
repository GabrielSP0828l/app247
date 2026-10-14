package com.jefiro.app247.infra.controller;

import com.jefiro.app247.infra.service.TestarWebhookMP;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class TestarWebhookMPControllerRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TestarWebhookMP.class, () -> mock(TestarWebhookMP.class))
            .withUserConfiguration(TestarWebhookMPController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void configuracaoPadraoNaoRegistraEndpointsDeTeste() {
        assertThat(applicationContext.getBeansOfType(TestarWebhookMPController.class)).isEmpty();
    }

    @Test
    void ausenciaDaPropriedadeNaoRegistraEndpointsDeTeste() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TestarWebhookMPController.class));
    }

    @Test
    void propriedadeFalseNaoRegistraEndpointsDeTeste() {
        runner.withPropertyValues("app.test-endpoints.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TestarWebhookMPController.class));
    }

    @Test
    void habilitacaoExplicitaRegistraEndpointsDeTeste() {
        runner.withPropertyValues("app.test-endpoints.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TestarWebhookMPController.class));
    }

    @Test
    void profileProdNuncaRegistraEndpointsDeTeste() {
        runner.withPropertyValues("app.test-endpoints.enabled=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).doesNotHaveBean(TestarWebhookMPController.class));
    }
}
