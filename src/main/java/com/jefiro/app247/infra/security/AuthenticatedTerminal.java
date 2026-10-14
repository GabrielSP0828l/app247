package com.jefiro.app247.infra.security;

import java.security.Principal;

/** Identidade imutável derivada exclusivamente da credencial individual do dispositivo. */
public record AuthenticatedTerminal(String terminalId, String empresaId, String condominioId,
                                    int credentialVersion) implements Principal {
    @Override
    public String getName() {
        return terminalId;
    }
}
