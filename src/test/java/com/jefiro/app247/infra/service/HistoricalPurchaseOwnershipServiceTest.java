package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.CustomerIdentity;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.CustomerIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricalPurchaseOwnershipServiceTest {
    @Mock CustomerIdentityRepository identities;
    @Mock CustomerIdentityHashService hashes;

    @Test void contaNaoVerificadaNaoFazClaim() {
        var service = new HistoricalPurchaseOwnershipService(identities, hashes);
        User user = User.builder().cpf("52998224725").build();
        ApiBusinessException error = assertThrows(ApiBusinessException.class, () -> service.claimVerified(user));
        assertEquals("CPF_NOT_VERIFIED", error.getCode());
        verifyNoInteractions(identities, hashes);
    }

    @Test void claimUsaCpfHashETenantDoVinculoAutorizado() {
        var service = new HistoricalPurchaseOwnershipService(identities, hashes);
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User user = User.builder().idUser("user-a").empresa(empresa).cpf("52998224725")
                .cpfVerifiedAt(Instant.now()).build();
        CustomerIdentity identity = new CustomerIdentity(); identity.setEmpresa(empresa);
        when(hashes.hashCpf("52998224725")).thenReturn("hash-a");
        when(identities.findForClaim("empresa-a", "hash-a")).thenReturn(Optional.of(identity));

        assertTrue(service.claimVerified(user));
        assertSame(user, identity.getUser());
        assertNotNull(identity.getClaimedAt());
        verify(identities, never()).findForClaim(eq("empresa-b"), any());
    }

    @Test void cpfDiferenteNaoReivindicaIdentidade() {
        var service = new HistoricalPurchaseOwnershipService(identities, hashes);
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User user = User.builder().empresa(empresa).cpf("11144477735").cpfVerifiedAt(Instant.now()).build();
        when(hashes.hashCpf("11144477735")).thenReturn("hash-b");
        when(identities.findForClaim("empresa-a", "hash-b")).thenReturn(Optional.empty());
        assertFalse(service.claimVerified(user));
    }
}
