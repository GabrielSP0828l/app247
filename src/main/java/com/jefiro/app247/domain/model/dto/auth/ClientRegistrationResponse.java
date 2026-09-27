package com.jefiro.app247.domain.model.dto.auth;

public record ClientRegistrationResponse(
        UserSummary user,
        String cpfVerificationStatus,
        Object condominio,
        boolean requiresLogin,
        boolean legalDocumentsConfigured
) {
    public record UserSummary(String id, String nome, String email) {}
}
