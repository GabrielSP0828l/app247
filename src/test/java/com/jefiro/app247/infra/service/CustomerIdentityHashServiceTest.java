package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdentityHashServiceTest {
    @Test
    void falhaRapidoSemSecretOuComSecretFraco() {
        assertThatThrownBy(() -> new CustomerIdentityHashService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_HASH_SECRET");
        assertThatThrownBy(() -> new CustomerIdentityHashService("curto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void hmacEhDeterministicoENaoExpoeCpf() {
        var service = new CustomerIdentityHashService("test-only-secret-with-more-than-32-characters");
        String first = service.hashCpf("52998224725");
        String second = service.hashCpf("52998224725");
        assertThat(first).isEqualTo(second).hasSize(64).doesNotContain("52998224725");
    }
}
