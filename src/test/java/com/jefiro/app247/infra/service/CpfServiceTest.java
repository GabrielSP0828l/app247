package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CpfServiceTest {
    private final CpfService service = new CpfService();

    @Test void aceitaCpfComESemMascara() {
        assertEquals("52998224725", service.requireValid("529.982.247-25"));
        assertEquals("11144477735", service.requireValid("11144477735"));
    }

    @Test void rejeitaDigitosErradosTamanhoESequencias() {
        assertAll(
                () -> assertThrows(ApiBusinessException.class, () -> service.requireValid("123")),
                () -> assertThrows(ApiBusinessException.class, () -> service.requireValid("00000000000")),
                () -> assertThrows(ApiBusinessException.class, () -> service.requireValid("52998224724"))
        );
    }
}
