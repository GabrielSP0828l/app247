package com.jefiro.app247.domain.model.dto;

import java.time.Instant;

/** A credencial bruta aparece somente na resposta transacional de criação/rotação. */
public record TerminalCredentialResponse(String terminalId, String credential, int version,
                                         Instant createdAt) {
}
